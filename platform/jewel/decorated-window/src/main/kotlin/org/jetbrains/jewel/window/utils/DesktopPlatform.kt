package org.jetbrains.jewel.window.utils

/** Identifies the desktop operating system the application is currently running on. */
public enum class DesktopPlatform {
    /** The Linux operating system. */
    Linux,

    /** The Windows operating system. */
    Windows,

    /** The macOS operating system. */
    MacOS,

    /** An unrecognized or unsupported operating system. */
    Unknown;

    /** Provides the [Current] platform detected from system properties. */
    public companion object {
        /** The [DesktopPlatform] detected from the `os.name` system property at runtime. */
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
