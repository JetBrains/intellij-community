package org.jetbrains.jewel.bridge.theme

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.ui.JBColor
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.toComposeColorOrUnspecified
import org.jetbrains.jewel.foundation.BorderColors
import org.jetbrains.jewel.foundation.GlobalColors
import org.jetbrains.jewel.foundation.OutlineColors

public fun GlobalColors.Companion.readFromLaF(): GlobalColors =
    GlobalColors(
        borders = BorderColors.readFromLaF(),
        outlines = OutlineColors.readFromLaF(),
        infoContent = retrieveColorOrUnspecified("*.infoForeground"),
        paneBackground = retrieveColorOrUnspecified("Panel.background"),
    )

public fun BorderColors.Companion.readFromLaF(): BorderColors =
    BorderColors(
        normal = JBColor.border().toComposeColorOrUnspecified(),
        focused = DarculaUIUtil.getOutlineColor(true, true).toComposeColorOrUnspecified(),
        disabled = DarculaUIUtil.getOutlineColor(false, false).toComposeColorOrUnspecified(),
    )

public fun OutlineColors.Companion.readFromLaF(): OutlineColors =
    OutlineColors(
        focused = retrieveColorOrUnspecified("*.focusColor"),
        focusedWarning = retrieveColorOrUnspecified("Component.warningFocusColor"),
        focusedError = retrieveColorOrUnspecified("Component.errorFocusColor"),
        warning = retrieveColorOrUnspecified("Component.inactiveWarningFocusColor"),
        error = retrieveColorOrUnspecified("Component.inactiveErrorFocusColor"),
    )
