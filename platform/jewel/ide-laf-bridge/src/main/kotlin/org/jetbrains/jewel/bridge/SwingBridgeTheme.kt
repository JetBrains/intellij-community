package org.jetbrains.jewel.bridge

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.ResourceLoader
import com.intellij.openapi.components.service
import com.intellij.util.ui.DirProvider
import org.jetbrains.jewel.ExperimentalJewelApi
import org.jetbrains.jewel.LocalResourceLoader
import org.jetbrains.jewel.themes.intui.standalone.IntUiDefaultResourceLoader
import org.jetbrains.jewel.themes.intui.standalone.IntUiTheme

private val bridgeService = service<SwingBridgeService>()

@ExperimentalJewelApi
@Composable
fun SwingBridgeTheme(content: @Composable () -> Unit) {
    val themeData by bridgeService.currentBridgeThemeData.collectAsState()

    // TODO handle non-Int UI themes, too
    IntUiTheme(themeData.themeDefinition, themeData.componentStyling, swingCompatMode = true) {
        CompositionLocalProvider(LocalResourceLoader provides IntelliJResourceLoader) {
            content()
        }
    }
}

object IntelliJResourceLoader : ResourceLoader {

    private val dirProvider = DirProvider()

    override fun load(resourcePath: String) =
        IntUiDefaultResourceLoader.loadOrNull(resourcePath)
            ?: IntUiDefaultResourceLoader.load(resourcePath.removePrefix(dirProvider.dir()))

}
