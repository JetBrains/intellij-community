@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename") // Going for consistency with other files

package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.ui.graphics.Color
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle

/** Creates an Int UI dark [CircularProgressStyle] with the provided parameters. */
public fun CircularProgressStyle.Companion.dark(
    frameTime: Duration = 125.milliseconds,
    color: Color = Color(0xFF6F737A),
): CircularProgressStyle = CircularProgressStyle(frameTime, color)

/** Creates an Int UI light [CircularProgressStyle] with the provided parameters. */
public fun CircularProgressStyle.Companion.light(
    frameTime: Duration = 125.milliseconds,
    color: Color = Color(0xFFA8ADBD),
): CircularProgressStyle = CircularProgressStyle(frameTime, color)
