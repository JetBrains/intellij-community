// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.openapi.ui.addKeyboardAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.PopupState
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.FilterComponent
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.Point
import java.awt.event.KeyEvent
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.KeyStroke

class DropDownComponentFactory<T : Any>(private val state: MutableStateFlow<T?>) {

  fun create(vmScope: CoroutineScope,
             filterName: @Nls String,
             valuePresenter: (T) -> @Nls String = Any::toString,
             chooseValue: suspend (RelativePoint, PopupState<JBPopup>) -> T?): JComponent {
    val popupState: PopupState<JBPopup> = PopupState.forPopup()

    return object : FilterComponent(Supplier<@NlsContexts.Label String?> { filterName }) {

      override fun getCurrentText(): String {
        val value = state.value
        return if (value != null) valuePresenter(value) else emptyFilterValue
      }

      override fun getEmptyFilterValue(): String = ""

      override fun isValueSelected(): Boolean = state.value != null

      override fun installChangeListener(onChange: Runnable) {
        vmScope.launch {
          state.collectLatest {
            onChange.run()
          }
        }
      }

      override fun createResetAction(): Runnable = Runnable { state.update { null } }

      override fun shouldDrawLabel(): DrawLabelMode = DrawLabelMode.WHEN_VALUE_NOT_SET
    }.apply {
      setShowPopupAction {
        val point = RelativePoint(this, Point(0, this.height + JBUIScale.scale(4)))
        if (popupState.isRecentlyHidden) return@setShowPopupAction
        vmScope.launch {
          val newValue = chooseValue(point, popupState)
          state.update { newValue }
        }
      }

      addKeyboardAction(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, 0)) {
        state.value = null
      }
    }.initUi().apply {
      UIUtil.setTooltipRecursively(this, filterName)
    }
  }

  fun create(vmScope: CoroutineScope,
             filterName: @Nls String,
             items: List<T>,
             valuePresenter: (T) -> @Nls String = Any::toString): JComponent =
    create(vmScope, filterName, valuePresenter) { point, popupState ->
      ChooserPopupUtil.showChooserPopup(point, popupState, items) {
        ChooserPopupUtil.PopupItemPresentation.Simple(valuePresenter(it))
      }
    }

  fun create(vmScope: CoroutineScope,
             filterName: @Nls String,
             items: List<T>,
             valuePresenter: (T) -> @Nls String = Any::toString,
             popupItemPresenter: (T) -> ChooserPopupUtil.PopupItemPresentation): JComponent =
    create(vmScope, filterName, valuePresenter) { point, popupState ->
      ChooserPopupUtil.showChooserPopup(point, popupState, items, popupItemPresenter)
    }
}