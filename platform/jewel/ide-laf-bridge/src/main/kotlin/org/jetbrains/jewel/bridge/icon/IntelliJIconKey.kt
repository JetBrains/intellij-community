package org.jetbrains.jewel.bridge.icon

import com.intellij.ui.icons.CachedImageIcon
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.icon.IntelliJIconKey

@Suppress("UnstableApiUsage") // We need to use internal APIs
public fun IntelliJIconKey.Companion.fromPlatformIcon(icon: javax.swing.Icon): IconKey {
    check(icon is CachedImageIcon) {
        "Only resource-backed CachedImageIcons are supported (e.g., coming from AllIcons)"
    }

    val oldUiPath =
        checkNotNull(icon.originalPath) {
            "Only resource-backed CachedImageIcons are supported (e.g., coming from AllIcons)"
        }

    val newUiPath = icon.expUIPath ?: oldUiPath
    return IntelliJIconKey(oldUiPath, newUiPath)
}
