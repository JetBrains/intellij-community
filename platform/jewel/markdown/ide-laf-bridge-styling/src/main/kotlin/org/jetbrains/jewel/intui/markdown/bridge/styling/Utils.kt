package org.jetbrains.jewel.intui.markdown.bridge.styling

import com.intellij.ui.JBColor

internal val isLightTheme: Boolean
    get() = JBColor.isBright()
