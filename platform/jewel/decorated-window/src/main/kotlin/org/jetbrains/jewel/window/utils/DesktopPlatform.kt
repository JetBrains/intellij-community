package org.jetbrains.jewel.window.utils

public enum class DesktopPlatform {
    Linux,
    Windows,
    MacOS,
    Unknown;

    public companion object {
        public val Current: DesktopPlatform by lazy {
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
