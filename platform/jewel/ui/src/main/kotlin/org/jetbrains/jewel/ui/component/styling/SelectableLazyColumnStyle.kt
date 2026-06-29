package org.jetbrains.jewel.ui.component.styling

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import org.jetbrains.jewel.foundation.GenerateDataFunctions

/** Combines item height and [SimpleListItemStyle] metrics for a selectable lazy column. */
@GenerateDataFunctions
public class SelectableLazyColumnStyle(
    /**
     * The intended height for list items. Note: currently stored on the style but not applied to items by the
     * SelectableLazyColumn component.
     */
    public val itemHeight: Dp,
    /** The style applied to each simple list item. */
    public val simpleListItemStyle: SimpleListItemStyle,
) {
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

    /** Companion object for [SelectableLazyColumnStyle]. */
    public companion object
}

/** CompositionLocal providing the [SelectableLazyColumnStyle] for the current theme. */
public val LocalSelectableLazyColumnStyle: ProvidableCompositionLocal<SelectableLazyColumnStyle> =
    staticCompositionLocalOf {
        error("No LocalSelectableLazyColumnStyle provided. Have you forgotten the theme?")
    }
