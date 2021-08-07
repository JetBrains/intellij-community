// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename.ui

import com.intellij.find.FindBundle
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts.Label
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.impl.RenameOptions
import com.intellij.refactoring.rename.impl.TextOptions
import com.intellij.refactoring.ui.NameSuggestionsField
import com.intellij.ui.UserActivityWatcher
import com.intellij.ui.layout.*
import java.awt.event.ActionEvent
import java.awt.event.ItemEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

internal class RenameDialog(
  private val project: Project,
  @Label private val presentableText: String,
  initOptions: Options,
) : DialogWrapper(project) {

  // model
  private var myTargetName: String = initOptions.targetName
  private var myCommentsStringsOccurrences: Boolean? = initOptions.renameOptions.textOptions.commentStringOccurrences
  private var myTextOccurrences: Boolean? = initOptions.renameOptions.textOptions.textOccurrences
  private var myScope: SearchScope = initOptions.renameOptions.searchScope
  var preview: Boolean = false
    private set

  private val myPreviewAction: Action = object : AbstractAction(RefactoringBundle.message("preview.button")) {
    override fun actionPerformed(e: ActionEvent) {
      preview = true
      okAction.actionPerformed(e)
    }
  }

  // ui
  private val myDialogPanel: DialogPanel = doCreateCenterPanel()

  init {
    title = RefactoringBundle.message("rename.title")
    setOKButtonText(RefactoringBundle.message("rename.title"))
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
  }

  override fun createCenterPanel(): JComponent = myDialogPanel

  override fun createActions(): Array<Action> {
    return arrayOf(okAction, cancelAction, helpAction, myPreviewAction)
  }

  private fun doCreateCenterPanel(): DialogPanel = panel {
    presentableText()
    newName()
    textOccurrenceCheckboxes()
    searchScope()
  }

  private fun LayoutBuilder.presentableText() {
    row {
      label(presentableText)
    }
  }

  private fun LayoutBuilder.newName() {
    row {
      val labelBuilder = label(RefactoringBundle.message("rename.dialog.new.name.label"))
      val nameSuggestionsField = NameSuggestionsField(arrayOf(myTargetName), project)
      nameSuggestionsField.addDataChangedListener {
        myTargetName = nameSuggestionsField.enteredName
      }
      nameSuggestionsField.invoke().constraints(growX).focused()
      labelBuilder.component.labelFor = nameSuggestionsField
    }
  }

  private fun LayoutBuilder.textOccurrenceCheckboxes() {
    if (myCommentsStringsOccurrences == null && myTextOccurrences == null) {
      return
    }
    row {
      cell(isFullWidth = true) {
        myCommentsStringsOccurrences?.let {
          checkBox(
            text = RefactoringBundle.getSearchInCommentsAndStringsText(),
            getter = { it },
            setter = { myCommentsStringsOccurrences = it }
          )
        }
        myTextOccurrences?.let {
          checkBox(
            text = RefactoringBundle.getSearchForTextOccurrencesText(),
            getter = { it },
            setter = { myTextOccurrences = it }
          )
        }
      }
    }
  }

  private fun LayoutBuilder.searchScope() {
    if (myScope is LocalSearchScope) {
      return
    }
    val scopeCombo = ScopeChooserCombo(project, true, true, myScope.displayName)
    Disposer.register(myDisposable, scopeCombo)
    scopeCombo.comboBox.addItemListener { event ->
      if (event.stateChange == ItemEvent.SELECTED) {
        myScope = scopeCombo.selectedScope ?: return@addItemListener
      }
    }
    row {
      val labelBuilder = label(FindBundle.message("find.scope.label"))
      scopeCombo.invoke()
      labelBuilder.component.labelFor = scopeCombo
    }
  }

  fun result(): Options = Options(
    targetName = myTargetName,
    renameOptions = RenameOptions(
      textOptions = TextOptions(
        commentStringOccurrences = myCommentsStringsOccurrences,
        textOccurrences = myTextOccurrences,
      ),
      searchScope = myScope
    )
  )

  data class Options(
    val targetName: String,
    val renameOptions: RenameOptions
  )
}
