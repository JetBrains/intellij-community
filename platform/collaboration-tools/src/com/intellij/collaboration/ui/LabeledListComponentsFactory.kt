// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.ui

import com.intellij.collaboration.ui.layout.SizeRestrictedSingleComponentLayout
import com.intellij.collaboration.ui.util.DimensionRestrictions
import com.intellij.icons.AllIcons
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.ui.InlineIconButton
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.WrapLayout
import com.intellij.util.ui.launchOnShow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.FlowLayout
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.math.max

/**
 * Set of factory methods to create components for displaying labeled lists of items
 * For example, a list of reviewers or labels in a code review
 */
object LabeledListComponentsFactory {

  /**
   * A panel with the label which maintains the preferred width equal to the max possible text length
   */
  fun createLabelPanel(
    listEmptyState: StateFlow<Boolean>,
    emptyText: @NlsContexts.Label String,
    notEmptyText: @NlsContexts.Label String,
  ): JPanel {
    val label = JLabel().apply {
      foreground = UIUtil.getContextHelpForeground()
      border = JBUI.Borders.empty(6, 0, 6, 5)

      launchOnShow("Labeled list label") {
        listEmptyState.collect {
          text = if (it) emptyText else notEmptyText
        }
      }
    }

    return JPanel(null).apply {
      layout = SizeRestrictedSingleComponentLayout().apply {
        prefSize = object : DimensionRestrictions {
          override fun getWidth() = label.getFontMetrics(label.font)?.let {
            max(it.stringWidth(emptyText), it.stringWidth(notEmptyText))
          }

          override fun getHeight() = null
        }
      }
      isOpaque = false

      add(label)
    }
  }

  /**
   * A panel with the list of items from [itemsState] which can be edited via [editActionHandler]
   * The panel will be wrapped if there's not enough horizontal space to display all items
   */
  fun <T : Any> createListPanel(
    itemsState: StateFlow<List<T>>,
    editActionHandler: suspend (JComponent, ActionEvent) -> Unit,
    clearActionHandler: ((ActionEvent) -> Unit)? = null,
    itemComponentFactory: (T) -> JComponent,
  ): JPanel {
    val editButton = InlineIconButton(AllIcons.General.Inline_edit).apply {
      withBackgroundHover = true

      launchOnShow("Labeled list edit button") {
        actionListener = ActionListener {
          launch {
            editActionHandler(this@apply, it)
          }
        }
        try {
          awaitCancellation()
        }
        finally {
          actionListener = null
        }
      }
    }

    val clearButton = clearActionHandler?.let { handler ->
      InlineIconButton(AllIcons.Actions.Close).apply {
        withBackgroundHover = true
        actionListener = ActionListener { handler(it) }
      }
    }

    return JPanel(WrapLayout(FlowLayout.LEADING, 0, 0)).apply {
      isOpaque = false

      launchOnShow("Labeled list panel") {
        itemsState.collect { newList ->
          removeAll()
          if (newList.isEmpty()) {
            add(editButton)
          }
          else {
            for (item in newList.dropLast(1)) {
              add(itemComponentFactory(item))
            }
            val lastItem = newList.last()
            // attach controls to the last items so they are moved to the next line together
            val itemWithControls = HorizontalListPanel().apply {
              add(itemComponentFactory(lastItem))
              add(editButton)
              if (clearButton != null) {
                add(clearButton)
              }
            }
            add(itemWithControls)
          }
          revalidate()
          repaint()
        }
      }
    }
  }

  fun <T : Any> createListPanel(
    itemsState: StateFlow<List<T>>,
    editActionHandler: suspend (JComponent, ActionEvent) -> Unit,
    itemComponentFactory: (T) -> JComponent,
  ): JPanel = createListPanel(itemsState, editActionHandler, null, itemComponentFactory)

  /**
   * Creates a grid panel with labeled lists where the labels column width is equal to the widest label
   */
  fun createGrid(listsWithLabels: List<Pair<JComponent, JComponent>>): JPanel {
    return JPanel(null).apply {
      isOpaque = false
      layout = MigLayout(LC()
                           .fillX()
                           .gridGap("0", "0")
                           .insets("0", "0", "0", "0"))

      listsWithLabels.forEach { (label, list) ->
        add(label, CC().alignY("top"))
        add(list, CC().minWidth("0").growX().pushX().wrap())
      }
    }
  }
}
