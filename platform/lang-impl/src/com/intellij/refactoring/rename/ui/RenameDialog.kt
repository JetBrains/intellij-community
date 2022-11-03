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
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.Gaps
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

  private fun Panel.presentableText() {
    row {
      label(RefactoringBundle.message("rename.0.and.its.usages.to", presentableText))
    }
  }

  private fun Panel.newName() {
    row {
      cell(NameSuggestionsField(arrayOf(myTargetName), project))
        .applyToComponent {
          addDataChangedListener {
            myTargetName = enteredName
          }
        }
        .focused()
        .label(RefactoringBundle.message("rename.dialog.new.name.label"))
        .widthGroup("")
        .align(AlignX.RIGHT)
    }
  }

  private fun Panel.textOccurrenceCheckboxes() {
    if (myCommentsStringsOccurrences == null && myTextOccurrences == null) {
      return
    }
    row {
      myCommentsStringsOccurrences?.let {
        this@row.checkBox(RefactoringBundle.getSearchInCommentsAndStringsText())
          .bindSelected({ it }, { myCommentsStringsOccurrences = it })
      }
      myTextOccurrences?.let {
        this@row.checkBox(RefactoringBundle.getSearchForTextOccurrencesText())
          .bindSelected({ it }, { myTextOccurrences = it })
      }
    }
  }

  private fun Panel.searchScope() {
    if (myScope is LocalSearchScope) {
      return
    }
    row {
      cell(ScopeChooserCombo(project, true, true, myScope.displayName))
        .applyToComponent {
          Disposer.register(myDisposable, this)
          comboBox.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
              myScope = selectedScope ?: return@addItemListener
            }
          }
        }
        // For some reason Scope and New name fields are misaligned - fix this here
        .customize(Gaps(right = 3))
        .label(FindBundle.message("find.scope.label"))
        .widthGroup("")
        .align(AlignX.RIGHT)
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
