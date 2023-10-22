@file:Suppress("MatchingDeclarationName", "ktlint:standard:filename") // Going for consistency with other files

package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.ui.component.styling.CircularProgressStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

fun CircularProgressStyle.Companion.dark(
    frameTime: Duration = 125.milliseconds,
    color: Color = Color(0xFF6F737A),
) = CircularProgressStyle(frameTime, color)

fun CircularProgressStyle.Companion.light(
    frameTime: Duration = 125.milliseconds,
    color: Color = Color(0xFFA8ADBD),
) = CircularProgressStyle(frameTime, color)
