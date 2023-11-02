package org.jetbrains.jewel.foundation.utils

internal enum class LogLevel(val color: String) {
    Trace("\u001b[38;5;33m"),
    Debug("\u001b[35;1m"),
    Info("\u001b[38;5;77m"),
    Warn("\u001b[33;1m"),
    Error("\u001b[31;1m"),
    Off(""),
}

internal interface Logger {

    var currentLogLevel: LogLevel

    // Resets previous color codes
    private fun resetColor() = "\u001b[0m"

    public fun log(level: LogLevel, msg: String)

    public fun e(msg: String) {
        log(LogLevel.Error, LogLevel.Error.color + msg + resetColor())
    }

    public fun d(msg: String) {
        log(LogLevel.Debug, LogLevel.Debug.color + msg + resetColor())
    }

    public fun w(msg: String) {
        log(LogLevel.Warn, LogLevel.Warn.color + msg + resetColor())
    }

    public fun i(msg: String) {
        log(LogLevel.Info, LogLevel.Info.color + msg + resetColor())
    }

    public fun t(msg: String) {
        log(LogLevel.Trace, LogLevel.Trace.color + msg + resetColor())
    }
}

// TODO remove and replace with real logger
@Deprecated("Use a real logger instead")
internal object Log : Logger {

    override var currentLogLevel: LogLevel = LogLevel.Off

    override fun log(level: LogLevel, msg: String) {
        if (currentLogLevel.ordinal <= level.ordinal) println(msg)
    }
}
