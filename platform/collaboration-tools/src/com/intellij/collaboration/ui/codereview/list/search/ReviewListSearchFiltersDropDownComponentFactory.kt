// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.ui.util.popup.PopupItemPresentation
import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.FilterComponent
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.Point
import java.awt.event.KeyEvent
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.KeyStroke

object ReviewListSearchFiltersDropDownComponentFactory {
  fun <T : Any> create(
    filterState: StateFlow<T?>,
    filterName: @Nls String,
    valuePresenter: (T) -> @Nls String = Any::toString,
    chooseFilterValue: suspend (RelativePoint) -> Unit,
    clearFilterValue: () -> Unit,
  ): JComponent {
    return object : FilterComponent(Supplier<@NlsContexts.Label String> { filterName }) {
      override fun getCurrentText(): String {
        val value = filterState.value
        return if (value != null) valuePresenter(value) else emptyFilterValue
      }

      override fun getEmptyFilterValue(): String = ""

      override fun isValueSelected(): Boolean = filterState.value != null

      override fun installChangeListener(onChange: Runnable) {
        launchOnShow("ReviewListDropDown") {
          filterState.collectLatest {
            onChange.run()
          }
        }
      }

      override fun createResetAction(): Runnable = Runnable { clearFilterValue() }

      override fun shouldDrawLabel(): DrawLabelMode = DrawLabelMode.WHEN_VALUE_NOT_SET
    }.apply {
      launchOnShow("ReviewListDropDownPopup") {
        setShowPopupAction {
          val point = RelativePoint(this@apply, Point(0, this@apply.height + JBUIScale.scale(4)))
          launch { chooseFilterValue(point) }
        }
        try {
          awaitCancellation()
        }
        finally {
          setShowPopupAction {}
        }
      }

      addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0)) {
        clearFilterValue()
      }
    }.initUi().apply {
      UIUtil.setTooltipRecursively(this, filterName)
    }
  }

  fun <T : Any> create(
    filterState: StateFlow<T?>,
    filterName: @Nls String,
    items: List<T>,
    onSelect: () -> Unit,
    valuePresenter: (T) -> @Nls String = Any::toString,
    popupItemPresenter: (T) -> PopupItemPresentation,
    chooseFilterValue: suspend (T?) -> Unit,
    clearFilterValue: () -> Unit,
  ): JComponent =
    create(
      filterState,
      filterName,
      valuePresenter,
      { point ->
        val selectedItem = ChooserPopupUtil.showChooserPopup(point, items, popupItemPresenter)
        if (selectedItem != null) {
          onSelect()
        }
        chooseFilterValue(selectedItem)
      },
      clearFilterValue
    )

  fun <T : Any> create(
    filterState: StateFlow<T?>,
    filterName: @Nls String,
    items: List<T>,
    onSelect: () -> Unit,
    valuePresenter: (T) -> @Nls String = Any::toString,
    chooseFilterValue: suspend (T?) -> Unit,
    clearFilterValue: () -> Unit,
  ): JComponent =
    create(
      filterState,
      filterName,
      items,
      onSelect,
      valuePresenter,
      { popupItem ->
        PopupItemPresentation.Simple(valuePresenter(popupItem))
      },
      chooseFilterValue,
      clearFilterValue
    )
}
