package org.jetbrains.jewel.ui.icon

import org.jetbrains.jewel.foundation.GenerateDataFunctions

public interface IconKey {
    public val iconClass: Class<*>

    public fun path(isNewUi: Boolean): String
}

@GenerateDataFunctions
public class PathIconKey(private val path: String, override val iconClass: Class<*>) : IconKey {
    override fun path(isNewUi: Boolean): String = path
}

@GenerateDataFunctions
public class IntelliJIconKey(
    public val oldUiPath: String,
    public val newUiPath: String,
    override val iconClass: Class<*>,
) : IconKey {
    override fun path(isNewUi: Boolean): String = if (isNewUi) newUiPath else oldUiPath

    public companion object
}
