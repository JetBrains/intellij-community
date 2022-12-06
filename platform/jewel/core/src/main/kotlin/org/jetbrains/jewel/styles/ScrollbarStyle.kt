package org.jetbrains.jewel.styles

import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.shape.RoundedCornerShape
import org.jetbrains.jewel.IntelliJMetrics
import org.jetbrains.jewel.IntelliJPalette

// TODO consider that scrollbars have different behaviors on different OSes
// Scrollbars on IJ do NOT follow the LaF defaults! They are only influenced by the ScrollbarUI being used, which depends on the OS.
//
// * On Win and Linux, it's pretty easy: follow what DefaultScrollbarUI does
//   * Except that you have different behavior if com.intellij.ui.components.ScrollSettings.isThumbSmallIfOpaque == true
// * On Mac, it follows the OS setting (legacy vs overlay)
//   * This involves JNI calls to get that setting, and to observe its changes (see com.intellij.ui.components.MacScrollBarUI.Style)
//   * The style is somewhat different depending on the style of scrollbars
//
// The standard Compose ScrollbarStyle lacks a lot of things, too, such as the ability to paint the track when needed,
// and things like thumb borders, etc.
fun ScrollbarStyle(palette: IntelliJPalette, metrics: IntelliJMetrics) = ScrollbarStyle(
    minimalHeight = metrics.scrollbar.minSize,
    thickness = metrics.scrollbar.thickness,
    shape = RoundedCornerShape(metrics.scrollbar.thumbCornerSize),
    hoverDurationMillis = DEFAULT_HOVER_DURATION_MILLIS,
    unhoverColor = palette.scrollbar.thumbIdleColor,
    hoverColor = palette.scrollbar.thumbHoverColor
)

// See com.intellij.ui.components.ScrollBarPainter.ScrollBarPainter: 11 frames, assuming 60fps (16 ms/f)
const val DEFAULT_HOVER_DURATION_MILLIS = 11 * 16
