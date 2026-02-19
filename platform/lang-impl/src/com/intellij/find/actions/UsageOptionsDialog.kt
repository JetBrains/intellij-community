// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.actions

import com.intellij.find.FindBundle.message
import com.intellij.find.FindUsagesSettings
import com.intellij.find.usages.api.UsageOptions
import com.intellij.find.usages.impl.AllSearchOptions
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.psi.search.SearchScope
import com.intellij.ui.UserActivityWatcher
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.event.ItemEvent
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JPanel

internal class UsageOptionsDialog(
  private val project: Project,
  @NlsContexts.Label presentableText: String?,
  allOptions: AllSearchOptions,
  private val showScopeChooser: Boolean,
  canReuseTab: Boolean
) : DialogWrapper(project) {

  // model
  private var myFindUsages: Boolean = allOptions.options.isUsages
  private var myScope: SearchScope = allOptions.options.searchScope
  private var myTextSearch: Boolean? = allOptions.textSearch

  // ui
  private val myDialogPanel: DialogPanel = panel {
    if (presentableText != null) {
      row {
        label(presentableText)
      }
    }

    group(message("find.what.group")) {
      row {
        checkBox(message("find.what.usages.checkbox"))
          .bindSelected(::myFindUsages)
      }
      myTextSearch?.let {
        row {
          checkBox(message("find.options.search.for.text.occurrences.checkbox"))
            .bindSelected({ it }, { myTextSearch = it })
        }
      }
    }.topGap(TopGap.NONE)

    if (showScopeChooser) {
      val scopeCombo = ScopeChooserCombo(project, true, true, myScope.displayName)
      Disposer.register(myDisposable, scopeCombo)
      scopeCombo.comboBox.addItemListener { event ->
        if (event.stateChange == ItemEvent.SELECTED) {
          myScope = scopeCombo.selectedScope ?: return@addItemListener
        }
      }
      group(message("find.scope.label")) {
        row {
          cell(scopeCombo)
            .align(AlignX.FILL)
            .focused()
        }
      }
    }
  }

  private val myCbOpenInNewTab: JBCheckBox? = if (canReuseTab) {
    JBCheckBox(message("find.open.in.new.tab.checkbox"), FindUsagesSettings.getInstance().isShowResultsInSeparateView)
  }
  else {
    null
  }

  init {
    title = message("find.usages.dialog.title")
    setOKButtonText(message("find.dialog.find.button"))
    init()
    installWatcher()
  }

  private fun installWatcher() {
    UserActivityWatcher().apply {
      addUserActivityListener(::stateChanged)
      register(myDialogPanel)
    }
  }

  private fun stateChanged() {
    myDialogPanel.apply() // for some reason DSL UI implementation only updates model from UI only within apply()
    isOKActionEnabled = myFindUsages || myTextSearch == true
  }

  override fun createCenterPanel(): JComponent = myDialogPanel

  override fun createActions(): Array<Action> = arrayOf(okAction, cancelAction, helpAction)

  override fun doOKAction() {
    super.doOKAction()
    FindUsagesSettings.getInstance().apply {
      if (showScopeChooser) {
        defaultScopeName = myScope.displayName
      }
      myCbOpenInNewTab?.let { checkbox ->
        isShowResultsInSeparateView = checkbox.isSelected
      }
    }
  }

  override fun createSouthAdditionalPanel(): JPanel? = myCbOpenInNewTab?.let { checkbox ->
    JPanel().apply {
      add(checkbox)
      border = JBUI.Borders.empty(0, UIUtil.DEFAULT_HGAP)
    }
  }

  fun result(): AllSearchOptions = AllSearchOptions(
    options = UsageOptions.createOptions(myFindUsages, myScope),
    textSearch = myTextSearch,
  )
}
