package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@GenerateDataFunctions
public class SelectableLazyColumnStyle(public val itemHeight: Dp, public val simpleListItemStyle: SimpleListItemStyle) {
    public companion object
}

public val LocalSelectableLazyColumnStyle: ProvidableCompositionLocal<SelectableLazyColumnStyle> =
    staticCompositionLocalOf {
        error("No LocalSelectableLazyColumnStyle provided. Have you forgotten the theme?")
    }
