package com.intellij.ide.customize.transferSettings.providers.vswin.utilities

// If minorVersion is set to -1, it means any minor version is supported and we don't care about minor version.
class Version2(var major: Int, var minor: Int = -1) {
    companion object {
        fun parse(s: String): Version2 {
            if (s.isEmpty()) {
                throw IllegalArgumentException("The version string can't be empty")
            }

            val parts = s.split('.')

            when {
                parts.size > 2 -> throw IllegalArgumentException("Too many dot-separated parts")
                parts.isEmpty() -> throw IllegalArgumentException("Too few dot-separated parts")
            }

            if (parts[0].toIntOrNull() == null || parts[1].toIntOrNull() == null) {
                throw IllegalArgumentException("Can't parse to int")
            }

            return Version2(parts[0].toInt(), parts[1].toInt())
        }
    }

    override operator fun equals(other: Any?): Boolean {
        if (other !is Version2) {
            return false
        }

        if (minor == -1 || other.minor == -1) {
            return major == other.major
        }

        return major == other.major && minor == other.minor
    }

    operator fun compareTo(other: Version2): Int {
        // todo: Current approach is terrible

        if (minor == -1 || other.minor == -1) {
            return major.compareTo(other.major)
        }

        return (major*1000+minor).compareTo(other.major*1000+other.minor)
    }

    override fun hashCode(): Int {
        return if (minor != -1) {
            31 * major + minor
        }
        else {
            31 * major
        }
    }

    override fun toString(): String {
        if (minor == -1) {
            return major.toString()
        }
        return "$major.$minor"
    }
}