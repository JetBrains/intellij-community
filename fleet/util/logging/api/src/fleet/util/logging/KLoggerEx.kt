// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import kotlin.coroutines.cancellation.CancellationException

@Suppress("NOTHING_TO_INLINE")
inline fun KLogger.assert(condition: Boolean, message: String) {
    if (!condition)
        error(message)
}

inline fun KLogger.assert(condition: Boolean, message: () -> String) {
    if (!condition)
        error(message)
}

inline fun <T> KLogger.catch(body: () -> T): T? {
    return internalCatch(body) { th ->
        this.error(th)
        null
    }
}

inline fun <T> KLogger.catch(message: () -> String, body: () -> T): T? {
    return internalCatch(body) { th ->
        this.error(th, message)
        null
    }
}

inline fun <T> KLogger.catchWarning(body: () -> T): T? {
    return internalCatch(body) { th ->
        this.warn(th)
        null
    }
}

inline fun <T> KLogger.catchWarning(message: () -> String, body: () -> T): T? {
    return internalCatch(body) { th ->
        this.warn(th, message)
        null
    }
}

inline fun <T> KLogger.catchReport(reporter: KLogger.(Throwable) -> Unit, body: () -> T): T? {
    return internalCatch(body) { th ->
        reporter(th)
        null
    }
}

// This method is used to log additional data when exception is processed upper on the stack where that data isn't
// accessible.
// We can't wrap exception since there can be logic based on exception type.
// We use `info` log level since exception itself can be logged in arbitrary level or not logged at all.
inline fun <T> KLogger.rethrow(message: () -> String, body: () -> T): T {
    return try {
        body()
    } catch (th: CancellationException) {
        throw th
    } catch (th: Throwable) {
        this.info { "${message()}: ${th.message}" }
        throw th
    }
}

inline fun <T> KLogger.internalCatch(body: () -> T, errorHandler: KLogger.(Throwable) -> T?): T? {
    return try {
        body()
    } catch (th: CancellationException) {
        throw th
    } catch (th: Throwable) {
        errorHandler(th)
    }
}

inline fun <T> KLogger.catchWithCancellationSuppress(body: () -> T): T? {
    return try {
        body()
    } catch (th: CancellationException) {
        if (isDebugEnabled) {
            // we produce a new exception here to have better diagnostics about the call site
            debug(Throwable("Exception caught in catchWithCancellationSuppress", th))
        }
        null
    } catch (th: Throwable) {
        error(th)
        null
    }
}

inline fun <T> KLogger.catchWithCancellationSuppress(message: () -> String, body: () -> T): T? {
    return try {
        body()
    } catch (th: CancellationException) {
        if (isDebugEnabled) {
            // we produce a new exception here to have better diagnostics about the call site
            debug(Throwable("Exception caught in catchWithCancellationSuppress", th), message)
        }
        null
    } catch (th: Throwable) {
        error(th, message)
        null
    }
}

