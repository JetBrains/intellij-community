package org.jetbrains.jewel.intui.standalone.styling

import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import org.jetbrains.jewel.styling.CircularProgressStyle
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Stable
data class IntUiCircularProgressStyle(
    override val frameTime: Duration,
    override val color: Color,
) : CircularProgressStyle {

    companion object {

        fun dark(
            frameTime: Duration = 125.milliseconds,
            color: Color = Color(0xFF6F737A),
        ) = IntUiCircularProgressStyle(frameTime, color)

        fun light(
            frameTime: Duration = 125.milliseconds,
            color: Color = Color(0xFFA8ADBD),
        ) = IntUiCircularProgressStyle(frameTime, color)
    }
}
