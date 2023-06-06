package org.jetbrains.jewel.foundation.utils

internal enum class LogLevel(val color: String) {
    Trace("\u001b[38;5;33m"),
    Debug("\u001b[35;1m"),
    Info("\u001b[38;5;77m"),
    Warn("\u001b[33;1m"),
    Error("\u001b[31;1m"),
    Off("")
}

internal interface Logger {

    var currentLogLevel: LogLevel

    // Resets previous color codes
    private fun resetColor() = "\u001b[0m"
    fun log(level: LogLevel, msg: String)
    fun e(msg: String) =
        log(LogLevel.Error, LogLevel.Error.color + msg + resetColor())

    fun d(msg: String) =
        log(LogLevel.Debug, LogLevel.Debug.color + msg + resetColor())

    fun w(msg: String) =
        log(LogLevel.Warn, LogLevel.Warn.color + msg + resetColor())

    fun i(msg: String) =
        log(LogLevel.Info, LogLevel.Info.color + msg + resetColor())

    fun t(msg: String) =
        log(LogLevel.Trace, LogLevel.Trace.color + msg + resetColor())
}

internal object Log : Logger {

    override var currentLogLevel: LogLevel = LogLevel.Off
    override fun log(level: LogLevel, msg: String) {
        if (currentLogLevel.ordinal <= level.ordinal) println(msg)
    }
}

fun main() {
    Log.currentLogLevel = LogLevel.Trace
    Log.e(Log.currentLogLevel.name)
    Log.t("this is a trace message")
    Log.d("this is a debug message")
    Log.i("this is an info message")
    Log.w("this is a warning message")
    Log.e("this is a severe message")
}
