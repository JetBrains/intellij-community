// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.customization

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.components.dialog
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.Function
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.components.BorderLayoutPanel
import org.jetbrains.annotations.Nls
import java.awt.Component
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.util.function.Supplier
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JList

/**
 * An actions list with search field and control buttons: add, remove, move and reset.
 *
 * @param [groupId] id of group that is used to show actions
 * @param [addActionGroupIds] ids of groups that are shown when Add action is called.
 * If empty all actions are shown by default.
 * Use [addActionHandler] to override default dialog.
 *
 * @see showDialog
 */
class CustomizeActionGroupPanel(
  val groupId: String,
  val addActionGroupIds: List<String> = emptyList(),
) : BorderLayoutPanel() {

  private val list: JBList<Any>

  /**
   * Handles Add action event for adding new actions. Returns a list of objects.
   *
   * @see CustomizationUtil.acceptObjectIconAndText
   */
  var addActionHandler: () -> List<Any>? = {
    ActionGroupPanel.showDialog(
      IdeBundle.message("group.customizations.add.action.group"),
      *addActionGroupIds.toTypedArray()
    )
  }

  init {
    list = JBList<Any>().apply {
      model = CollectionListModel(collectActions(groupId, CustomActionsSchema.getInstance()))
      cellRenderer = MyListCellRender()
    }

    preferredSize = Dimension(420, 300)

    addToTop(BorderLayoutPanel().apply {
      addToRight(createActionToolbar())
      addToCenter(createSearchComponent())
    })
    addToCenter(createCentralPanel(list))
  }

  private fun collectActions(groupId: String, schema: CustomActionsSchema): List<Any> {
    return ActionGroupPanel.getActions(listOf(groupId), schema).first().children
  }

  fun getActions(): List<Any> = (list.model as CollectionListModel).toList()

  private fun createActionToolbar(): JComponent {
    val group = DefaultActionGroup().apply {

      val addActionGroup = DefaultActionGroup().apply {
        templatePresentation.text = IdeBundle.message("group.customizations.add.action.group")
        templatePresentation.icon = AllIcons.General.Add
        isPopup = true
      }

      addActionGroup.addAll(
        AddAction(IdeBundle.messagePointer("button.add.action")) { selected, model ->
          addActionHandler()?.let { result ->
            result.forEachIndexed { i, value ->
              model.add(selected + i + 1, value)
            }
            list.selectedIndices = IntArray(result.size) { i -> selected + i + 1 }
          }
        },
        AddAction(IdeBundle.messagePointer("button.add.separator")) { selected, model ->
          model.add(selected + 1, Separator.create())
          list.selectedIndex = selected + 1
        }
      )
      add(addActionGroup)
      add(RemoveAction(IdeBundle.messagePointer("button.remove"), AllIcons.General.Remove))
      add(MoveAction(Direction.UP, IdeBundle.messagePointer("button.move.up"), AllIcons.Actions.MoveUp))
      add(MoveAction(Direction.DOWN, IdeBundle.messagePointer("button.move.down"), AllIcons.Actions.MoveDown))
      add(RestoreAction(IdeBundle.messagePointer("button.restore.all"), AllIcons.Actions.Rollback))
    }
    return ActionManager.getInstance().createActionToolbar("CustomizeActionGroupPanel", group, true).apply {
      targetComponent = list
      setReservePlaceAutoPopupIcon(false)
    }.component
  }

  private fun createSearchComponent(): Component {
    val speedSearch = object : ListSpeedSearch<Any>(list, Function {
      when (it) {
        is String -> ActionManager.getInstance().getAction(it).templateText
        else -> null
      }
    }) {
      override fun isPopupActive() = true
      override fun showPopup(searchText: String?) {}
      override fun isSpeedSearchEnabled() = false
      override fun showPopup() {}
    }
    val filterComponent = object : FilterComponent("CUSTOMIZE_ACTIONS", 5) {
      override fun filter() {
        speedSearch.findAndSelectElement(filter)
        speedSearch.component.repaint()
      }
    }
    for (keyCode in intArrayOf(KeyEvent.VK_HOME, KeyEvent.VK_END, KeyEvent.VK_UP, KeyEvent.VK_DOWN)) {
      object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) {
          val filter: String = filterComponent.filter
          if (!StringUtil.isEmpty(filter)) {
            speedSearch.adjustSelection(keyCode, filter)
          }
        }
      }.registerCustomShortcutSet(keyCode, 0, filterComponent.textEditor)
    }
    return filterComponent
  }

  private fun createCentralPanel(list: JBList<Any>): Component {
    return Wrapper(ScrollPaneFactory.createScrollPane(list)).apply {
      border = JBUI.Borders.empty(3)
    }
  }

  companion object {
    fun showDialog(groupId: String, sourceGroupIds: List<String>, @Nls title: String, customize: CustomizeActionGroupPanel.() -> Unit = {}): List<Any>? {
      val panel = CustomizeActionGroupPanel(groupId, sourceGroupIds)
      panel.preferredSize = Dimension(480, 360)
      panel.customize()
      val dialog = dialog(
        title = title,
        panel = panel,
        resizable = true,
        focusedComponent = panel.list
      )
      return if (dialog.showAndGet()) panel.getActions() else null
    }
  }

  private inner class AddAction(
    text: Supplier<String>,
    val block: (selected: Int, model: CollectionListModel<Any>) -> Unit
  ) : DumbAwareAction(text, Presentation.NULL_STRING, null) {
    override fun actionPerformed(e: AnActionEvent) {
      val selected = list.selectedIndices?.lastOrNull() ?: (list.model.size - 1)
      val model = (list.model as CollectionListModel)
      block(selected, model)
    }
  }

  private inner class MoveAction(
    val direction: Direction,
    text: Supplier<String>,
    icon: Icon
  ) : DumbAwareAction(text, Presentation.NULL_STRING, icon) {

    init {
      registerCustomShortcutSet(direction.shortcut, list)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val model = list.model as CollectionListModel
      val indices = list.selectedIndices
      for (i in indices.indices) {
        val index = indices[i]
        model.exchangeRows(index, index + direction.sign)
        indices[i] = index + direction.sign
      }
      list.selectedIndices = indices
      list.scrollRectToVisible(list.getCellBounds(indices.first(), indices.last()))
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = direction.test(list.selectedIndices, list.model.size)
    }
  }

  private inner class RemoveAction(
    text: Supplier<String>,
    icon: Icon
  ) : DumbAwareAction(text, Presentation.NULL_STRING, icon) {

    init {
      registerCustomShortcutSet(CommonShortcuts.getDelete(), list)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val model = list.model as CollectionListModel
      val selectedIndices = list.selectedIndices
      selectedIndices.reversedArray().forEach(model::remove)
      selectedIndices.firstOrNull()?.let { list.selectedIndex = it.coerceAtMost(model.size - 1) }
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = list.selectedIndices.isNotEmpty()
    }
  }

  private inner class RestoreAction(
    text: Supplier<String>,
    icon: Icon
  ) : DumbAwareAction(text, Presentation.NULL_STRING, icon) {

    private val defaultActions = collectActions(groupId, CustomActionsSchema())

    override fun actionPerformed(e: AnActionEvent) {
      list.model = CollectionListModel(defaultActions)
    }

    override fun update(e: AnActionEvent) {
      val current = (list.model as CollectionListModel).items
      e.presentation.isEnabled = defaultActions != current
    }
  }

  enum class Direction(val sign: Int, val shortcut: ShortcutSet, val test: (index: IntArray, size: Int) -> Boolean) {
    UP(-1, CommonShortcuts.MOVE_UP, { index, _ -> index.isNotEmpty() && index.first() >= 1 }),
    DOWN(1, CommonShortcuts.MOVE_DOWN, { index, size -> index.isNotEmpty() && index.last() < size - 1 });
  }

  private class MyListCellRender : ColoredListCellRenderer<Any>() {
    override fun customizeCellRenderer(list: JList<out Any>, value: Any?, index: Int, selected: Boolean, hasFocus: Boolean) {
      CustomizationUtil.acceptObjectIconAndText(value) { t, i ->
        SpeedSearchUtil.appendFragmentsForSpeedSearch(list, t, SimpleTextAttributes.REGULAR_ATTRIBUTES, selected, this)
        icon = i
      }
    }
  }
}