// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import org.jetbrains.jewel.bridge.retrieveColor
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveInsetsAsPaddingValues
import org.jetbrains.jewel.bridge.toComposeColor
import org.jetbrains.jewel.ui.component.styling.SpeedSearchColors
import org.jetbrains.jewel.ui.component.styling.SpeedSearchIcons
import org.jetbrains.jewel.ui.component.styling.SpeedSearchMetrics
import org.jetbrains.jewel.ui.component.styling.SpeedSearchStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal fun readSpeedSearchStyle(): SpeedSearchStyle =
    SpeedSearchStyle(
        colors =
            SpeedSearchColors(
                background =
                    retrieveColorOrUnspecified("SpeedSearch.background").takeOrElse {
                        retrieveColor("Editor.SearchField.background", UIUtil.getTextFieldBackground().toComposeColor())
                    },
                border =
                    retrieveColorOrUnspecified("SpeedSearch.borderColor").takeOrElse {
                        retrieveColor("Editor.Toolbar.borderColor", JBColor.LIGHT_GRAY.toComposeColor())
                    },
                foreground =
                    retrieveColorOrUnspecified("SpeedSearch.foreground").takeOrElse {
                        retrieveColor("TextField.foreground", UIUtil.getToolTipForeground().toComposeColor())
                    },
                error =
                    retrieveColorOrUnspecified("SpeedSearch.errorForeground").takeOrElse {
                        retrieveColor("SearchField.errorForeground", JBColor.RED.toComposeColor())
                    },
            ),
        metrics = SpeedSearchMetrics(retrieveInsetsAsPaddingValues("SpeedSearch.borderInsets", PaddingValues(4.dp))),
        icons = SpeedSearchIcons(AllIconsKeys.Actions.Search),
    )
