// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.nullize
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.awt.Point
import javax.swing.Icon
import javax.swing.JTextField

class ReviewListSearchTextFieldFactory(private val searchState: MutableStateFlow<String?>) {

  fun create(viewScope: CoroutineScope, chooseFromHistory: suspend (RelativePoint) -> Unit): JTextField {
    val searchField = ExtendableTextField()
    searchField.addActionListener {
      val text = searchField.text.nullize()
      searchState.update { text }
    }
    viewScope.launch {
      searchState.collect {
        if (searchField.text.nullize() != it) searchField.text = it
      }
    }

    searchField.addExtension(object : ExtendableTextComponent.Extension {
      override fun isIconBeforeText(): Boolean = true
      override fun getIcon(hovered: Boolean): Icon = AllIcons.Actions.SearchWithHistory

      override fun getActionOnClick(): Runnable = Runnable {
        val point = createHistoryPoint(searchField)
        viewScope.launch {
          chooseFromHistory(point)
        }
      }
    })


    searchField.toolTipText = IdeBundle.message("tooltip.search.history.hotkey", KeymapUtil.getShortcutText("ShowSearchHistory"))

    DumbAwareAction.create {
      val point = createHistoryPoint(searchField)
      viewScope.launch {
        chooseFromHistory(point)
      }
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("ShowSearchHistory"), searchField)

    return searchField
  }

  private fun createHistoryPoint(searchField: ExtendableTextField) =
    RelativePoint(searchField, Point(JBUIScale.scale(2), searchField.height))
}