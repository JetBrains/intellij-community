package org.jetbrains.jewel.markdown.extensions.github.alerts

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions
import org.jetbrains.jewel.ui.icon.IconKey

@GenerateDataFunctions
public class AlertStyling(
    public val note: NoteAlertStyling,
    public val tip: TipAlertStyling,
    public val important: ImportantAlertStyling,
    public val warning: WarningAlertStyling,
    public val caution: CautionAlertStyling,
) {
    public companion object
}

public sealed interface BaseAlertStyling {
    public val padding: PaddingValues
    public val lineWidth: Dp
    public val lineColor: Color
    public val pathEffect: PathEffect?
    public val strokeCap: StrokeCap
    public val titleTextStyle: TextStyle
    public val titleIconKey: IconKey?
    public val titleIconTint: Color
    public val textColor: Color
}

@GenerateDataFunctions
public class NoteAlertStyling(
    override val padding: PaddingValues,
    override val lineWidth: Dp,
    override val lineColor: Color,
    override val pathEffect: PathEffect?,
    override val strokeCap: StrokeCap,
    override val titleTextStyle: TextStyle,
    override val titleIconKey: IconKey?,
    override val titleIconTint: Color,
    override val textColor: Color,
) : BaseAlertStyling {
    public companion object
}

@GenerateDataFunctions
public class TipAlertStyling(
    override val padding: PaddingValues,
    override val lineWidth: Dp,
    override val lineColor: Color,
    override val pathEffect: PathEffect?,
    override val strokeCap: StrokeCap,
    override val titleTextStyle: TextStyle,
    override val titleIconKey: IconKey?,
    override val titleIconTint: Color,
    override val textColor: Color,
) : BaseAlertStyling {
    public companion object
}

@GenerateDataFunctions
public class ImportantAlertStyling(
    override val padding: PaddingValues,
    override val lineWidth: Dp,
    override val lineColor: Color,
    override val pathEffect: PathEffect?,
    override val strokeCap: StrokeCap,
    override val titleTextStyle: TextStyle,
    override val titleIconKey: IconKey?,
    override val titleIconTint: Color,
    override val textColor: Color,
) : BaseAlertStyling {
    public companion object
}

@GenerateDataFunctions
public class WarningAlertStyling(
    override val padding: PaddingValues,
    override val lineWidth: Dp,
    override val lineColor: Color,
    override val pathEffect: PathEffect?,
    override val strokeCap: StrokeCap,
    override val titleTextStyle: TextStyle,
    override val titleIconKey: IconKey?,
    override val titleIconTint: Color,
    override val textColor: Color,
) : BaseAlertStyling {
    public companion object
}

@GenerateDataFunctions
public class CautionAlertStyling(
    override val padding: PaddingValues,
    override val lineWidth: Dp,
    override val lineColor: Color,
    override val pathEffect: PathEffect?,
    override val strokeCap: StrokeCap,
    override val titleTextStyle: TextStyle,
    override val titleIconKey: IconKey?,
    override val titleIconTint: Color,
    override val textColor: Color,
) : BaseAlertStyling {
    public companion object
}
