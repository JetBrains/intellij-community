package org.jetbrains.jewel.bridge

import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.ResourcePainterProvider

/** [ResourcePainterProvider] to resolve resources in Intellij Module and Bridge module. */
public fun bridgePainterProvider(path: String): ResourcePainterProvider =
    ResourcePainterProvider(path, DirProvider::class.java.classLoader, SwingBridgeService::class.java.classLoader)

/** [ResourcePainterProvider] to resolve resources in Intellij Module and Bridge module. */
public fun bridgePainterProvider(iconKey: IconKey): ResourcePainterProvider {
    val isNewUi = isNewUiTheme()
    return ResourcePainterProvider(
        iconKey.path(isNewUi),
        DirProvider::class.java.classLoader,
        SwingBridgeService::class.java.classLoader,
    )
}
