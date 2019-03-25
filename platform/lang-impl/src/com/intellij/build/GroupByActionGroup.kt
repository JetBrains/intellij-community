// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.build

import com.intellij.build.BuildViewGroupingSupport.Companion.SOURCE_ROOT_GROUPING
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.JBColor
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.PopupListElementRenderer
import java.awt.Graphics

class GroupByActionGroup(private val view: BuildView) : DefaultActionGroup("Group By", true), DumbAware {
  init {
    templatePresentation.icon = AllIcons.Actions.GroupBy
  }

  override fun canBePerformed(context: DataContext): Boolean = true

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = BuildViewGroupingSupport.KEY.getData(view) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val group = DefaultActionGroup().apply {
      addSeparator(e.presentation.text)
      add(GroupBySourceRootsAction(view))
    }

    val popup = SelectGroupingActionPopup(group, e.dataContext)
    when (val component = e.inputEvent?.component) {
      is ActionButtonComponent -> popup.showUnderneathOf(component)
      else -> popup.showInBestPositionFor(e.dataContext)
    }
  }
}

private class SelectGroupingActionPopup(group: ActionGroup, dataContext: DataContext) : PopupFactoryImpl.ActionGroupPopup(
  null, group, dataContext, false, false, false, true, null, -1, null, null) {
  override fun getListElementRenderer() = object : PopupListElementRenderer<Any>(this) {
    override fun createSeparator() = object : SeparatorWithText() {
      init {
        textForeground = JBColor.BLACK
        setCaptionCentered(false)
      }

      override fun paintLine(g: Graphics, x: Int, y: Int, width: Int) = Unit
    }
  }
}

private abstract class GroupingAction(private val view: BuildView) : ToggleAction(), DumbAware {
  init {
    isEnabledInModalContext = true
  }

  abstract val groupingKey: String

  override fun update(e: AnActionEvent): Unit = super.update(e).also {
    e.presentation.isEnabledAndVisible = getGroupingSupport(e)?.isAvailable(groupingKey) ?: false
  }

  override fun isSelected(e: AnActionEvent): Boolean = e.presentation.isEnabledAndVisible &&
                                                       getGroupingSupport(e)?.get(groupingKey) ?: false

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    getGroupingSupport(e)!![groupingKey] = state
  }

  private fun getGroupingSupport(e: AnActionEvent): BuildViewGroupingSupport? = BuildViewGroupingSupport.KEY.getData(view)
}

private class GroupBySourceRootsAction(view: BuildView) : GroupingAction(view) {
  init {
    templatePresentation.text = "Source root"
  }

  override val groupingKey: String get() = SOURCE_ROOT_GROUPING
}