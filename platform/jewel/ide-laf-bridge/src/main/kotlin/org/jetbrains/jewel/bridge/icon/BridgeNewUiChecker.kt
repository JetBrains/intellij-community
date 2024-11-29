package org.jetbrains.jewel.bridge.icon

import org.jetbrains.jewel.bridge.isNewUiTheme
import org.jetbrains.jewel.ui.icon.NewUiChecker

internal object BridgeNewUiChecker : NewUiChecker {
    override fun isNewUi(): Boolean = isNewUiTheme()
}
