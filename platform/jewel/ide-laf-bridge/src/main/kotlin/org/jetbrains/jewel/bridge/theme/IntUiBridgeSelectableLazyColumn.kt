package org.jetbrains.jewel.bridge.theme

import com.intellij.util.ui.JBUI
import org.jetbrains.jewel.bridge.toNonNegativeDpSize
import org.jetbrains.jewel.ui.component.styling.SelectableLazyColumnStyle

internal fun readSelectableLazyColumnStyle(): SelectableLazyColumnStyle =
    SelectableLazyColumnStyle(
        itemHeight = JBUI.CurrentTheme.ComboBox.minimumSize().toNonNegativeDpSize().height,
        simpleListItemStyle = readSimpleListItemStyle(),
    )
