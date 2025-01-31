package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

@GenerateDataFunctions
public class SelectableLazyColumnStyle(public val itemHeight: Dp, public val simpleListItemStyle: SimpleListItemStyle) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SelectableLazyColumnStyle

        if (itemHeight != other.itemHeight) return false
        if (simpleListItemStyle != other.simpleListItemStyle) return false

        return true
    }

    override fun hashCode(): Int {
        var result = itemHeight.hashCode()
        result = 31 * result + simpleListItemStyle.hashCode()
        return result
    }

    override fun toString(): String {
        return "SelectableLazyColumnStyle(" +
            "itemHeight=$itemHeight, " +
            "simpleListItemStyle=$simpleListItemStyle" +
            ")"
    }

    public companion object
}

public val LocalSelectableLazyColumnStyle: ProvidableCompositionLocal<SelectableLazyColumnStyle> =
    staticCompositionLocalOf {
        error("No LocalSelectableLazyColumnStyle provided. Have you forgotten the theme?")
    }
