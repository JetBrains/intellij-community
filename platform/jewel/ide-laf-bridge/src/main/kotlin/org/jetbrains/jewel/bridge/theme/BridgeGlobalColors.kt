package org.jetbrains.jewel.bridge.theme

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.bridge.toComposeColorOrUnspecified
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.OutlineColors
import org.jetbrains.jewel.foundation.TextColors

/** Creates a [GlobalColors] instance by reading the values from the current IntelliJ LaF. */
public fun GlobalColors.Companion.readFromLaF(): GlobalColors =
    GlobalColors(
        borders = BorderColors.readFromLaF(),
        outlines = OutlineColors.readFromLaF(),
        text = TextColors.readFromLaF(),
        panelBackground = UIUtil.getPanelBackground().toComposeColor(),
        toolwindowBackground = JBUI.CurrentTheme.ToolWindow.background().toComposeColor(),
    )

/** Creates a [BorderColors] instance by reading the values from the current IntelliJ LaF. */
public fun BorderColors.Companion.readFromLaF(): BorderColors =
    BorderColors(
        normal = JBColor.border().toComposeColorOrUnspecified(),
        focused = DarculaUIUtil.getOutlineColor(true, true).toComposeColorOrUnspecified(),
        disabled = DarculaUIUtil.getOutlineColor(false, false).toComposeColorOrUnspecified(),
    )

/** Creates a [TextColors] instance by reading the values from the current IntelliJ LaF. */
public fun TextColors.Companion.readFromLaF(): TextColors =
    TextColors(
        normal = JBUI.CurrentTheme.Label.foreground().toComposeColor(),
        selected = JBUI.CurrentTheme.Label.foreground(true).toComposeColor(),
        disabled = JBUI.CurrentTheme.Label.disabledForeground().toComposeColor(),
        disabledSelected = JBUI.CurrentTheme.Label.disabledForeground(true).toComposeColor(),
        info = JBUI.CurrentTheme.ContextHelp.FOREGROUND.toComposeColor(),
        error = JBUI.CurrentTheme.Label.errorForeground().toComposeColor(),
        warning = JBUI.CurrentTheme.Label.warningForeground().toComposeColor(),
    )

/** Creates an [OutlineColors] instance by reading the values from the current IntelliJ LaF. */
public fun OutlineColors.Companion.readFromLaF(): OutlineColors =
    OutlineColors(
        focused = JBUI.CurrentTheme.Focus.focusColor().toComposeColor(),
        focusedWarning = JBUI.CurrentTheme.Focus.warningColor(true).toComposeColor(),
        focusedError = JBUI.CurrentTheme.Focus.errorColor(true).toComposeColor(),
        warning = JBUI.CurrentTheme.Focus.warningColor(false).toComposeColor(),
        error = JBUI.CurrentTheme.Focus.errorColor(false).toComposeColor(),
    )
