package me.rhunk.snapenhance.common.ui

import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.coroutines.*

inline fun <T> MutableState<T>.asyncSet(
    coroutineScope: CoroutineScope,
    crossinline getter: () -> T
): MutableState<T> {
    coroutineScope.launch(Dispatchers.Main) {
        value = withContext(Dispatchers.IO) {
            getter()
        }
    }
    return this
}

fun <T> SnapshotStateList<T>.asyncSet(
    coroutineScope: CoroutineScope,
    getter: () -> List<T>
): SnapshotStateList<T> {
    coroutineScope.launch(Dispatchers.Main) {
        clear()
        addAll(withContext(Dispatchers.IO) {
            getter()
        })
    }
    return this
}

class AsyncUpdateDispatcher {
    private val callbacks = mutableListOf<() -> Unit>()

    fun dispatch() {
        callbacks.forEach { it() }
    }

    fun addCallback(callback: () -> Unit) {
        callbacks.add(callback)
    }

    fun removeCallback(callback: () -> Unit) {
        callbacks.remove(callback)
    }
}

@Composable
fun rememberAsyncUpdateDispatcher(): AsyncUpdateDispatcher {
    return remember { AsyncUpdateDispatcher() }
}

@Composable
inline fun <T> rememberAsyncMutableState(
    defaultValue: T,
    updateDispatcher: AsyncUpdateDispatcher? = null,
    keys: Array<*> = emptyArray<Any>(),
    crossinline getter: () -> T,
): MutableState<T> {
    val coroutineScope = rememberCoroutineScope()
    return remember { mutableStateOf(defaultValue) }.apply {
        var asyncSetCallback by remember { mutableStateOf({}) }

        LaunchedEffect(Unit) {
            asyncSetCallback = { asyncSet(coroutineScope, getter) }
            updateDispatcher?.addCallback(asyncSetCallback)
        }

        DisposableEffect(Unit) {
            onDispose { updateDispatcher?.removeCallback(asyncSetCallback) }
        }

        LaunchedEffect(*keys) {
            asyncSet(coroutineScope, getter)
        }
    }
}

@Composable
fun <T> rememberAsyncMutableStateList(
    defaultValue: List<T>,
    updateDispatcher: AsyncUpdateDispatcher? = null,
    keys: Array<*> = emptyArray<Any>(),
    getter: () -> List<T>,
): SnapshotStateList<T> {
    val coroutineScope = rememberCoroutineScope()

    return remember { mutableStateListOf<T>().apply {
        addAll(defaultValue)
    } }.apply {
        var asyncCallback by remember { mutableStateOf({}) }
        LaunchedEffect(Unit) {
            asyncCallback = {
                asyncSet(coroutineScope, getter)
            }
            updateDispatcher?.addCallback(asyncCallback)
        }

        DisposableEffect(Unit) {
            onDispose {
                updateDispatcher?.removeCallback(asyncCallback)
            }
        }

        LaunchedEffect(*keys) {
            asyncSet(coroutineScope, getter)
        }
    }
}

