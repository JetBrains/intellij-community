// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.collaboration.ui.util.bindIn
import com.intellij.collaboration.ui.util.name
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.Action
import javax.swing.JList

@ApiStatus.Internal
class SimpleComboboxWithActionsFactory<T : Any>(
  private val mappingsState: StateFlow<Collection<T>>,
  private val selectionState: MutableStateFlow<T?>,
) {
  fun create(
    scope: CoroutineScope,
    presenter: (T) -> Presentation,
    actions: StateFlow<List<Action>> = MutableStateFlow(emptyList()),
    sortComparator: Comparator<T> = Comparator.comparing { presenter(it).name },
  ): ComboBox<*> {

    val comboModel = ComboBoxWithActionsModel<T>().apply {
      bindIn(scope, mappingsState, selectionState, actions, sortComparator)

      if (selectedItem == null) {
        selectFirst()
      }
    }

    return ComboBox(comboModel).apply {
      renderer = object : ColoredListCellRenderer<ComboBoxWithActionsModel.Item<T>>() {
        override fun customizeCellRenderer(
          list: JList<out ComboBoxWithActionsModel.Item<T>>,
          value: ComboBoxWithActionsModel.Item<T>?,
          index: Int,
          selected: Boolean,
          hasFocus: Boolean,
        ) {
          if (value is ComboBoxWithActionsModel.Item.Wrapper) {
            val presentations = presenter(value.wrappee)
            append(presentations.name)
            presentations.secondaryName?.let {
              append(" ").append(it, SimpleTextAttributes.GRAYED_ATTRIBUTES)
            }
          }
          if (value is ComboBoxWithActionsModel.Item.Action) {
            if (model.size == index) border = IdeBorderFactory.createBorder(SideBorder.TOP)
            append(value.action.name.orEmpty())
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
          is ComboBoxWithActionsModel.Item.Action -> it.action.name.orEmpty()
        }
      }
    }
  }

  data class Presentation(val name: @Nls String, val secondaryName: @Nls String?)
}