package org.jetbrains.jewel.util

private val osName = System.getProperty("os.name")

fun isMacOs(): Boolean = osName.startsWith("mac", ignoreCase = true)

fun isWindows(): Boolean = osName.startsWith("windows", ignoreCase = true)

fun isLinux(): Boolean = osName.startsWith("linux", ignoreCase = true)
