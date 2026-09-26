// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.list.search

import com.intellij.collaboration.ui.util.bindTextIn
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.fields.ExtendableTextComponent
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.text.nullize
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.awt.Point
import javax.swing.Icon
import javax.swing.JTextField

internal object ReviewListSearchFiltersTextFieldFactory {
  fun create(
    searchTextFlow: Flow<String?>,
    setSearchText: ((String) -> Unit)? = null,
    submitSearchText: (String?) -> Unit,
    chooseFromHistory: suspend (RelativePoint) -> Unit
  ): JTextField {
    val searchField = ExtendableTextField()
    searchField.addActionListener {
      val text = searchField.text.nullize()
      submitSearchText(text)
    }
    searchField.launchOnShow("ReviewListTextField") {
      searchField.bindTextIn(this, searchTextFlow.map { it.nullize() ?: "" }) {
        setSearchText?.invoke(it)
      }
    }

    var onShowHistoryCallback: (() -> Unit)? = null
    searchField.launchOnShow("ReviewListTextFieldHistoryPoint") {
      onShowHistoryCallback = {
        val point = createHistoryPoint(searchField)
        launch { chooseFromHistory(point) }
      }
      try {
        awaitCancellation()
      }
      finally {
        onShowHistoryCallback = null
      }
    }

    searchField.addExtension(object : ExtendableTextComponent.Extension {
      override fun isIconBeforeText(): Boolean = true
      override fun getIcon(hovered: Boolean): Icon = AllIcons.Actions.SearchWithHistory
      override fun getActionOnClick(): Runnable = Runnable {
        onShowHistoryCallback?.invoke()
      }
    })

    searchField.toolTipText = IdeBundle.message("tooltip.search.history.hotkey", KeymapUtil.getShortcutText("ShowSearchHistory"))

    DumbAwareAction.create {
      onShowHistoryCallback?.invoke()
    }.registerCustomShortcutSet(KeymapUtil.getActiveKeymapShortcuts("ShowSearchHistory"), searchField)

    return searchField
  }

  private fun createHistoryPoint(searchField: ExtendableTextField) =
    RelativePoint(searchField, Point(JBUIScale.scale(2), searchField.height))
}
