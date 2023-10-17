package org.jetbrains.jewel.window.utils

enum class DesktopPlatform {
    Linux,
    Windows,
    MacOS,
    Unknown,
    ;

    companion object {
        val Current: DesktopPlatform by lazy {
            val name = System.getProperty("os.name")
            when {
                name?.startsWith("Linux") == true -> Linux
                name?.startsWith("Win") == true -> Windows
                name == "Mac OS X" -> MacOS
                else -> Unknown
            }
        }
    }
}
