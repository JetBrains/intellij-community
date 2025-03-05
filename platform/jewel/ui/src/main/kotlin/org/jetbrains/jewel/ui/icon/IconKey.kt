package org.jetbrains.jewel.ui.icon

import org.jetbrains.jewel.foundation.GenerateDataFunctions

public interface IconKey {
    public val iconClass: Class<*>

    public fun path(isNewUi: Boolean): String
}

@GenerateDataFunctions
public class PathIconKey(private val path: String, override val iconClass: Class<*>) : IconKey {
    override fun path(isNewUi: Boolean): String = path

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PathIconKey

        if (path != other.path) return false
        if (iconClass != other.iconClass) return false

        return true
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + iconClass.hashCode()
        return result
    }

    override fun toString(): String = "PathIconKey(path='$path', iconClass=$iconClass)"
}

@GenerateDataFunctions
public class IntelliJIconKey(
    public val oldUiPath: String,
    public val newUiPath: String,
    override val iconClass: Class<*>,
) : IconKey {
    override fun path(isNewUi: Boolean): String = if (isNewUi) newUiPath else oldUiPath

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as IntelliJIconKey

        if (oldUiPath != other.oldUiPath) return false
        if (newUiPath != other.newUiPath) return false
        if (iconClass != other.iconClass) return false

        return true
    }

    override fun hashCode(): Int {
        var result = oldUiPath.hashCode()
        result = 31 * result + newUiPath.hashCode()
        result = 31 * result + iconClass.hashCode()
        return result
    }

    override fun toString(): String {
        return "IntelliJIconKey(" +
            "oldUiPath='$oldUiPath', " +
            "newUiPath='$newUiPath', " +
            "iconClass=$iconClass" +
            ")"
    }

    public companion object
}
