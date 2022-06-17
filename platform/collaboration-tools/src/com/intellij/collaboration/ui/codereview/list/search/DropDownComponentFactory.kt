// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.ui.SimpleFocusBorder
import com.intellij.icons.AllIcons
import com.intellij.ui.ClickListener
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.Cursor
import java.awt.Point
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.*

class DropDownComponentFactory<T : Any>(private val state: MutableStateFlow<T?>) {

  fun create(vmScope: CoroutineScope,
             filterName: @Nls String,
             valuePresenter: (T) -> @Nls String = Any::toString,
             chooseValue: suspend (RelativePoint) -> T?): JComponent {
    var valueSet = false

    val text = JLabel().apply {
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      toolTipText = filterName
      isFocusable = false
    }

    val icon = JLabel().apply {
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      isFocusable = false
    }

    fun showChooserPopup() {
      val point = RelativePoint(text, Point(0, text.height + JBUIScale.scale(4)))
      vmScope.launch {
        val newValue = chooseValue(point)
        state.update { newValue }
      }
    }

    text.also { label ->
      object : ClickListener() {
        override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
          showChooserPopup()
          return true
        }
      }.installOn(label)
    }


    object : ClickListener() {
      override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
        if (valueSet) state.update { null }
        else showChooserPopup()
        return true
      }
    }.installOn(icon)

    vmScope.launch {
      state.collectLatest {
        if (it == null) {
          icon.icon = AllIcons.General.LinkDropTriangle
          text.text = filterName
          text.foreground = UIUtil.getContextHelpForeground()
          valueSet = false
        }
        else {
          icon.icon = AllIcons.Actions.Close
          text.text = valuePresenter(it)
          text.foreground = UIUtil.getLabelForeground()
          valueSet = true
        }
      }
    }

    return JPanel(HorizontalLayout(0, SwingConstants.CENTER)).apply {
      cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      isFocusable = true
      border = JBUI.Borders.compound(SimpleFocusBorder(), JBUI.Borders.empty(0, 2))

      registerKeyboardAction(ActionListener {
        showChooserPopup()
      }, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), JComponent.WHEN_FOCUSED)

      registerKeyboardAction(ActionListener {
        state.update { null }
      }, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), JComponent.WHEN_FOCUSED)

      add(text)
      add(icon)
    }
  }


  fun create(vmScope: CoroutineScope,
             filterName: @Nls String,
             items: List<T>,
             valuePresenter: (T) -> @Nls String = Any::toString): JComponent =
    create(vmScope, filterName, valuePresenter) { point ->
      ChooserPopupUtil.showChooserPopup(point, items) {
        ChooserPopupUtil.PopupItemPresentation.Simple(valuePresenter(it))
      }
    }

  fun create(vmScope: CoroutineScope,
             filterName: @Nls String,
             items: List<T>,
             valuePresenter: (T) -> @Nls String = Any::toString,
             popupItemPresenter: (T) -> ChooserPopupUtil.PopupItemPresentation): JComponent =
    create(vmScope, filterName, valuePresenter) { point ->
      ChooserPopupUtil.showChooserPopup(point, items, popupItemPresenter)
    }

}
