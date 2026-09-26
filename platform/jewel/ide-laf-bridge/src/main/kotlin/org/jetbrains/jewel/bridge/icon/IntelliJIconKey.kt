package org.jetbrains.jewel.bridge.icon

import com.intellij.ui.icons.IconPathProvider
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

/**
 * Creates an [IntelliJIconKey] from a Swing [icon] that implements [IconPathProvider], extracting the old-UI and New-UI
 * resource paths.
 *
 * @param icon The Swing icon to convert. Must be an [IconPathProvider] (e.g., icons from `AllIcons`).
 * @param iconClass The class whose [ClassLoader] will be used to locate the icon resource.
 */
public fun IntelliJIconKey.Companion.fromPlatformIcon(
    icon: javax.swing.Icon,
    iconClass: Class<*> = icon::class.java,
): IconKey {
    check(icon is IconPathProvider) {
        "Only icons implementing IconPathProvider are supported (e.g., coming from AllIcons)"
    }

    val oldUiPath =
        checkNotNull(icon.originalPath) {
            "Only resource-backed CachedImageIcons are supported (e.g., coming from AllIcons)"
        }

    val newUiPath = icon.expUIPath ?: oldUiPath
    return IntelliJIconKey(oldUiPath, newUiPath, iconClass)
}
