package org.jetbrains.jewel.ui.component.styling

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@Stable
@GenerateDataFunctions
public class TextAreaStyle(override val colors: TextAreaColors, override val metrics: TextAreaMetrics) :
    InputFieldStyle {
    public companion object
}

@Immutable
@GenerateDataFunctions
public class TextAreaColors(
    override val background: Color,
    override val backgroundDisabled: Color,
    override val backgroundFocused: Color,
    override val backgroundPressed: Color,
    override val backgroundHovered: Color,
    override val content: Color,
    override val contentDisabled: Color,
    override val contentFocused: Color,
    override val contentPressed: Color,
    override val contentHovered: Color,
    override val border: Color,
    override val borderDisabled: Color,
    override val borderFocused: Color,
    override val borderPressed: Color,
    override val borderHovered: Color,
    override val caret: Color,
    override val caretDisabled: Color,
    override val caretFocused: Color,
    override val caretPressed: Color,
    override val caretHovered: Color,
    public val placeholder: Color,
) : InputFieldColors {
    public companion object
}

@Stable
@GenerateDataFunctions
public class TextAreaMetrics(
    override val borderWidth: Dp,
    override val contentPadding: PaddingValues,
    override val cornerSize: CornerSize,
    override val minSize: DpSize,
) : InputFieldMetrics {
    public companion object
}

public val LocalTextAreaStyle: ProvidableCompositionLocal<TextAreaStyle> = staticCompositionLocalOf {
    error("No TextAreaStyle provided. Have you forgotten the theme?")
}
