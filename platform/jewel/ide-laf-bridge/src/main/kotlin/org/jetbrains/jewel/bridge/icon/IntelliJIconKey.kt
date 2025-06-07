package org.jetbrains.jewel.bridge.icon

import com.intellij.ui.icons.IconPathProvider
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

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
