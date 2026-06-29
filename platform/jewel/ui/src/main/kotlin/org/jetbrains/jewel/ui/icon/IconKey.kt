package org.jetbrains.jewel.ui.icon

import com.intellij.platform.icons.design.IconDesigner
import com.intellij.platform.icons.modifiers.IconModifier
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** Represents a key that resolves to an icon path, associated with a class loader. */
public interface IconKey {
    /** The class used to locate the icon resource on the classpath. */
    public val iconClass: Class<*>

    /** Returns the classpath-relative resource path for this icon, selecting the New UI or Classic UI variant. */
    public fun path(isNewUi: Boolean): String
}

/**
 * Renders the icon described by [iconKey] in this [IconDesigner], using the New UI path and applying [modifier].
 *
 * @param iconKey The [IconKey] identifying the icon resource.
 * @param modifier The [IconModifier] to apply to the icon. Defaults to the identity modifier.
 */
public fun IconDesigner.iconKey(iconKey: IconKey, modifier: IconModifier = IconModifier) {
    image(iconKey.path(isNewUi = true), iconKey.iconClass.classLoader, modifier)
}

/** An [IconKey] that resolves to a single fixed icon [path], regardless of the UI mode. */
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

/** An [IconKey] that resolves to different icon paths for the old and new IntelliJ UI. */
@GenerateDataFunctions
public class IntelliJIconKey(
    /** The classpath-relative resource path used when the Classic UI is active. */
    public val oldUiPath: String,
    /** The classpath-relative resource path used when the New UI is active. */
    public val newUiPath: String,
    /** The class used to locate the icon resource on the classpath. */
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

    /** Companion object for [IntelliJIconKey]. */
    public companion object
}
