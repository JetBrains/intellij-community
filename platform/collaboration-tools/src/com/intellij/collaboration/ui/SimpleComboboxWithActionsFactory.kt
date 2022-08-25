// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.collaboration.ui.util.getName
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.*
import org.jetbrains.annotations.Nls
import javax.swing.JList

class SimpleComboboxWithActionsFactory<T : Any>(
  private val model: ComboBoxWithActionsModel<T> // TODO: replace with viewmodel
) {

  fun create(presenter: (T) -> Presentation): ComboBox<*> =
    ComboBox(model).apply {
      renderer = object : ColoredListCellRenderer<ComboBoxWithActionsModel.Item<T>>() {
        override fun customizeCellRenderer(list: JList<out ComboBoxWithActionsModel.Item<T>>,
                                           value: ComboBoxWithActionsModel.Item<T>?,
                                           index: Int,
                                           selected: Boolean,
                                           hasFocus: Boolean) {
          if (value is ComboBoxWithActionsModel.Item.Wrapper) {
            val presentations = presenter(value.wrappee)
            append(presentations.name)
            presentations.secondaryName?.let {
              append(" ").append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
          }
          if (value is ComboBoxWithActionsModel.Item.Action) {
            if (model.size == index) border = IdeBorderFactory.createBorder(SideBorder.TOP)
            append(value.action.getName())
          }
        }
      }
      isUsePreferredSizeAsMinimum = false
      isOpaque = false
      isSwingPopup = true
    }.also { combo ->
      ComboboxSpeedSearch.installSpeedSearch(combo) {
        when (it) {
          is ComboBoxWithActionsModel.Item.Wrapper -> presenter(it.wrappee).name
          is ComboBoxWithActionsModel.Item.Action -> it.action.getName()
        }
      }
    }

  data class Presentation(val name: @Nls String, val secondaryName: @Nls String?)
}