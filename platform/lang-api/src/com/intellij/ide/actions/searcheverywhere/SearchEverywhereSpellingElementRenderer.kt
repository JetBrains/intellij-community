package com.intellij.ide.actions.searcheverywhere

import com.intellij.ui.SimpleColoredComponent
import com.intellij.util.ui.EmptyIcon
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

internal class SearchEverywhereSpellingElementRenderer : SimpleColoredComponent(), ListCellRenderer<SearchEverywhereSpellCheckResult.Correction> {
  override fun getListCellRendererComponent(list: JList<out SearchEverywhereSpellCheckResult.Correction>,
                                            value: SearchEverywhereSpellCheckResult.Correction,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    clear()
    ipad.right = 2
    ipad.left = 2
    icon = EmptyIcon.ICON_16
    append(value.presentationText)
    return this
  }
}