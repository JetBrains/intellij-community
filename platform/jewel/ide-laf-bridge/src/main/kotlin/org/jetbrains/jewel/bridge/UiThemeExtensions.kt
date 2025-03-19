package org.jetbrains.jewel.bridge

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo

@Suppress("UnstableApiUsage")
internal fun currentUiThemeOrNull(): UIThemeLookAndFeelInfo? =
    LafManager.getInstance().currentUIThemeLookAndFeel?.takeIf { it.isInitialized }
