package org.jetbrains.jewel.bridge

import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.ui.icon.IconKey
import org.jetbrains.jewel.ui.painter.ResourcePainterProvider

/** [ResourcePainterProvider] to resolve resources in Intellij Module and Bridge module. */
@Deprecated("Use IconKeys and the Icon composable instead")
public fun bridgePainterProvider(path: String): ResourcePainterProvider =
    ResourcePainterProvider(path, DirProvider::class.java.classLoader, SwingBridgeReader::class.java.classLoader)

/** [ResourcePainterProvider] to resolve resources in Intellij Module and Bridge module. */
@Deprecated("Use the Icon composable instead")
public fun bridgePainterProvider(iconKey: IconKey): ResourcePainterProvider {
    val isNewUi = isNewUiTheme()
    return ResourcePainterProvider(
        iconKey.path(isNewUi),
        DirProvider::class.java.classLoader,
        SwingBridgeReader::class.java.classLoader,
    )
}
