// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui

import com.intellij.analysis.AnalysisBundle
import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.icons.AllIcons
import com.intellij.ide.util.scopeChooser.ScopeChooserConfigurable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.profile.codeInspection.ui.table.ScopesOrderTable
import com.intellij.psi.search.scope.NonProjectFilesScope
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import java.awt.Component
import javax.swing.JComponent

internal class ScopesOrderDialog(
  parent: Component,
  private val myInspectionProfile: InspectionProfileImpl,
  private val myProject: Project,
) : DialogWrapper(parent, true) {

  private val myOptionsTable = ScopesOrderTable()
  private val myPanel: DialogPanel

  init {
    reloadScopeList()

    val listPanel = ToolbarDecorator.createDecorator(myOptionsTable)
      .setMoveDownAction { myOptionsTable.moveDown() }
      .setMoveUpAction { myOptionsTable.moveUp() }
      .addExtraAction(object : AnAction(CodeInsightBundle.messagePointer("action.AnActionButton.text.edit.scopes"), AllIcons.Actions.Edit) {
        override fun actionPerformed(e: AnActionEvent) {
          ShowSettingsUtil.getInstance().editConfigurable(myProject, ScopeChooserConfigurable(myProject))
          reloadScopeList()
        }

        override fun getActionUpdateThread(): ActionUpdateThread {
          return ActionUpdateThread.EDT
        }
      })
      .disableRemoveAction()
      .disableAddAction()
      .createPanel()

    myPanel = panel {
      row {
        cell(listPanel)
          .align(Align.FILL)
          .comment(AnalysisBundle.message("inspections.settings.scopes.order.help.label"))
      }.resizableRow()
    }

    init()
    title = AnalysisBundle.message("inspections.settings.scopes.order.title")
  }

  private fun reloadScopeList() {
    val allScope = CustomScopesProviderEx.getAllScope()
    val scopes = NamedScopesHolder.getAllNamedScopeHolders(myProject)
      .flatMap { it.scopes.asIterable() }
      .filter { it !is NonProjectFilesScope && it != allScope }
      .sortedWith(compareBy(ScopeOrderComparator(myInspectionProfile)) { it.scopeId })

    myOptionsTable.updateItems(scopes)
  }

  override fun createCenterPanel(): JComponent {
    return myPanel
  }

  override fun doOKAction() {
    val size = myOptionsTable.model.rowCount
    val newScopeOrder = mutableListOf<String>()
    for (i in 0..<size) {
      val namedScope = checkNotNull(myOptionsTable.getScopeAt(i))
      newScopeOrder.add(namedScope.scopeId)
    }
    if (newScopeOrder != myInspectionProfile.scopesOrder) {
      myInspectionProfile.setScopesOrder(newScopeOrder)
    }
    super.doOKAction()
  }
}
