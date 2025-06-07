package org.jetbrains.jewel.bridge.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.dp
import org.jetbrains.jewel.bridge.retrieveColorOrUnspecified
import org.jetbrains.jewel.bridge.retrieveIntAsDpOrUnspecified
import org.jetbrains.jewel.ui.component.styling.LazyTreeIcons
import org.jetbrains.jewel.ui.component.styling.LazyTreeMetrics
import org.jetbrains.jewel.ui.component.styling.LazyTreeStyle
import org.jetbrains.jewel.ui.component.styling.SimpleListItemColors
import org.jetbrains.jewel.ui.component.styling.SimpleListItemMetrics
import org.jetbrains.jewel.ui.icons.AllIconsKeys

internal fun readLazyTreeStyle(): LazyTreeStyle {
    val normalContent = retrieveColorOrUnspecified("Tree.foreground")
    val selectedContent = retrieveColorOrUnspecified("Tree.selectionForeground")
    val selectedElementBackground = retrieveColorOrUnspecified("Tree.selectionBackground")
    val inactiveSelectedElementBackground = retrieveColorOrUnspecified("Tree.selectionInactiveBackground")

    val itemColors =
        SimpleListItemColors(
            content = normalContent,
            contentActive = normalContent,
            contentSelected = selectedContent,
            contentSelectedActive = selectedContent,
            background = Color.Unspecified,
            backgroundActive = Color.Unspecified,
            backgroundSelected = inactiveSelectedElementBackground,
            backgroundSelectedActive = selectedElementBackground,
        )

    val leftIndent = retrieveIntAsDpOrUnspecified("Tree.leftChildIndent").takeOrElse { 7.dp }
    val rightIndent = retrieveIntAsDpOrUnspecified("Tree.rightChildIndent").takeOrElse { 11.dp }

    return LazyTreeStyle(
        colors = itemColors,
        metrics =
            LazyTreeMetrics(
                indentSize = leftIndent + rightIndent,
                simpleListItemMetrics =
                    SimpleListItemMetrics(
                        innerPadding = PaddingValues(horizontal = 12.dp),
                        outerPadding = PaddingValues(4.dp),
                        selectionBackgroundCornerSize = CornerSize(JBUI.CurrentTheme.Tree.ARC.dp / 2),
                        iconTextGap = 2.dp,
                    ),
                elementMinHeight = retrieveIntAsDpOrUnspecified("Tree.rowHeight").takeOrElse { 24.dp },
                chevronContentGap = 2.dp, // See com.intellij.ui.tree.ui.ClassicPainter.GAP
            ),
        icons =
            LazyTreeIcons(
                chevronCollapsed = AllIconsKeys.General.ChevronRight,
                chevronExpanded = AllIconsKeys.General.ChevronDown,
                chevronSelectedCollapsed = AllIconsKeys.General.ChevronRight,
                chevronSelectedExpanded = AllIconsKeys.General.ChevronDown,
            ),
    )
}
