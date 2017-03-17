/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.rename

import com.intellij.CommonBundle
import com.intellij.lang.findUsages.DescriptiveNameUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory
import com.intellij.refactoring.ui.NameSuggester
import com.intellij.refactoring.ui.NameSuggesterSelection
import com.intellij.refactoring.ui.nameSuggester
import com.intellij.refactoring.util.TextOccurrencesUtil
import com.intellij.ui.noria.*
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import com.intellij.xml.util.XmlTagUtilBase
import java.awt.GridBagConstraints
import java.awt.GridBagLayout

fun getFullName(psiElement: PsiElement): String {
  val name = DescriptiveNameUtil.getDescriptiveName(psiElement)
  val type = UsageViewUtil.getType(psiElement)
  return if (StringUtil.isEmpty(name)) type else "$type '$name'"
}

fun getLabelText(fullName: String): String {
  return RefactoringBundle.message("rename.0.and.its.usages.to", fullName)
}

fun invokeRefactoring(processor: BaseRefactoringProcessor, isPreview: Boolean, cb: () -> Unit) {
  processor.setPrepareSuccessfulSwingThreadCallback(cb)
  processor.setPreviewUsages(isPreview)
  processor.run()
}

fun validate(project: Project, psiElement: PsiElement, newName: String) =
  if (!RenameUtil.isValidName(project, psiElement, newName)) {
    false to "\'$newName\' is not a valid name"
  }
  else {
    val inputValidator = RenameInputValidatorRegistry.getInputErrorValidator(psiElement)
    if (inputValidator != null) {
      val errorText = inputValidator.`fun`(newName)
      if (errorText != null) {
        false to errorText
      }
      else true to null
    }
    else true to null
  }

fun createRenameDialog2(psiElement: PsiElement,
                        editor: Editor?,
                        nameSuggestionContext: PsiElement?,
                        processor: RenamePsiElementProcessor): RenameDialog2 {
  val suggestedNamesData = processor.getSuggestedNames(psiElement, nameSuggestionContext)
  val suggestedNameInfo = suggestedNamesData.second
  val suggestedNames = suggestedNamesData.first
  val project = psiElement.project
  val searchInComments: VarCell<Boolean> = cell(processor.isToSearchInComments(psiElement))
  val searchTextOccurrences: VarCell<Boolean> = cell(processor.isToSearchForTextOccurrences(psiElement))
  val searchTextOccurrencesEnabled = TextOccurrencesUtil.isSearchTextOccurencesEnabled(psiElement)
  val searchForReferences: VarCell<Boolean>? = run {
    if (processor.isToSearchForReferencesEnabled(psiElement)) cell(
      processor.isToSearchForReferences(psiElement))
    else null
  }
  val factories = Extensions.getExtensions(AutomaticRenamerFactory.EP_NAME)
    .filter { factory -> factory.isApplicable(psiElement) && factory.optionName != null }
  val factoriesFlags = factories.map { it to it.isEnabled }.toMap(hashMapOf())
  val performRename: (String, Boolean, callback: () -> Unit) -> Unit = { newName, isPreview, callback ->
    val renameProcessor = RenameProcessor(project,
                                          psiElement,
                                          newName,
                                          searchInComments.value,
                                          searchTextOccurrences.value)
    factories.filter { it.isEnabled }.forEach { renameProcessor.addRenamerFactory(it) }
    invokeRefactoring(renameProcessor, isPreview, callback)
  }

  val saveSettings: (String) -> Unit = { newName ->
    processor.setToSearchInComments(psiElement, searchInComments.value)
    processor.setToSearchForTextOccurrences(psiElement, searchTextOccurrences.value)
    if (searchForReferences != null) {
      processor.setToSearchForReferences(psiElement, searchForReferences.value)
    }
    factoriesFlags.forEach { factory, b -> factory.isEnabled = b }
    suggestedNameInfo?.nameChosen(newName)
  }
  val initialSelection = when {
    psiElement is PsiFile && editor == null -> NameSuggesterSelection.NameWithoutExtension
    editor == null || editor.settings.isPreselectRename -> NameSuggesterSelection.All
    else -> NameSuggesterSelection.None
  }
  return RenameDialog2(project = psiElement.project,
                       psiElement = psiElement,
                       editor = editor,
                       hasHelp = true,
                       saveSettings = saveSettings,
                       suggestedNames = suggestedNames,
                       suggestedNameInfo = suggestedNameInfo,
                       validate = { validate(project, psiElement, it) },
                       searchInComments = searchInComments,
                       searchTextOccurrences = searchTextOccurrences,
                       searchTextOccurrencesEnabled = searchTextOccurrencesEnabled,
                       searchForReferences = searchForReferences,
                       factoriesFlags = factoriesFlags,
                       performRename = performRename,
                       beforeCheckboxHook = {},
                       initialSelection = initialSelection)
}

fun createRenameDialog2(psiElement: PsiElement,
                        editor: Editor?,
                        nameSuggestionContext: PsiElement?): RenameDialog2 =
  createRenameDialog2(psiElement, editor, nameSuggestionContext, RenamePsiElementProcessor.forElement(psiElement))

fun showRenameDialogSimple(psiElement: PsiElement,
                           editor: Editor?,
                           nameSuggestionContext: PsiElement?,
                           performRename: (String, Boolean, RenameDialog2) -> Unit) {
  val d = createRenameDialog2(psiElement,
                              editor,
                              nameSuggestionContext)
  d.performRename = { newName, isPreview, callback ->
    performRename(newName, isPreview, d)
    callback.invoke()
  }
  d.show()
}

data class RenameDialog2(var project: Project,
                         var psiElement: PsiElement,
                         var editor: Editor?,
                         var hasHelp: Boolean,
                         var searchInComments: VarCell<Boolean>,
                         var searchTextOccurrences: VarCell<Boolean>,
                         var searchTextOccurrencesEnabled: Boolean,
                         var searchForReferences: VarCell<Boolean>?,
                         var suggestedNames: List<String>,
                         var suggestedNameInfo: SuggestedNameInfo?,
                         var factoriesFlags: MutableMap<AutomaticRenamerFactory, Boolean>,
                         var performRename: (String, Boolean, callback: () -> Unit) -> Unit,
                         var saveSettings: (String) -> Unit,
                         var validate: (String) -> Pair<Boolean, String?>,
                         var beforeCheckboxHook: ElementBuilder<*>.() -> Unit,
                         var initialSelection: (String) -> TextRange)
fun RenameDialog2.show() {
  PsiUtilCore.ensureValid(psiElement)
  val nameLabel = XmlStringUtil.wrapInHtml(XmlTagUtilBase.escapeString(getLabelText(getFullName(psiElement)), false))
  val oldName = UsageViewUtil.getShortName(psiElement)
  val newName = cell(suggestedNames.filterNotNull().firstOrNull() ?: "")
  val validation = cell { validate(newName.value) }
  val isEnabled = cell { validation.value.first && newName.value != oldName }
  val errorText = cell { validation.value.second }

  val renamePanel = component<Unit>("renamePanel") { u, ch ->
    panel {
      props = Panel(layout = GridBagLayout())
      label {
        props = Label(nameLabel).apply {
          key = "name"
          constraints = GridBagConstraints().apply {
            insets = JBUI.insetsBottom(4)
            weighty = 0.0
            weightx = 1.0
            gridwidth = GridBagConstraints.REMAINDER
            fill = GridBagConstraints.BOTH
          }
        }
      }
      nameSuggester {
        key = "name suggester"
        props = NameSuggester(suggestedNames = suggestedNames,
                              selection = initialSelection,
                              onChange = { newName.value = it },
                              project = project,
                              fileType = FileTypes.PLAIN_TEXT,
                              editor = editor).apply {
          autoFocus = true
          constraints = GridBagConstraints().apply {
            insets = JBUI.insetsBottom(8)
            gridwidth = 2
            fill = GridBagConstraints.BOTH
            weightx = 1.0
            gridx = 0
            weighty = 1.0
          }
        }
      }
      ch.forEach {
        child(it)
      }
    }
  }

  val northPanel = component<Unit>("north") { u, ch ->
    renamePanel {
      props = Unit
      beforeCheckboxHook()
      if (searchForReferences != null) {
        checkbox {
          key = "searchForRefs"
          props = Checkbox(text = RefactoringBundle.message("search.for.references"),
                           selected = searchForReferences!!.value,
                           onChange = { searchForReferences!!.value = it }).apply {
            constraints = GridBagConstraints().apply {
              insets = JBUI.insetsBottom(4)
              gridwidth = 1
              gridx = 0
              weighty = 0.0
              weightx = 1.0
              fill = GridBagConstraints.BOTH
            }
          }
        }
      }
      checkbox {
        key = "searchInComments"
        props = Checkbox(text = RefactoringBundle.getSearchInCommentsAndStringsText(),
                         selected = searchInComments.value,
                         onChange = { searchInComments.value = it }).apply {
          constraints = GridBagConstraints().apply {
            insets = JBUI.insetsBottom(4)
            gridwidth = 1
            gridx = 0
            weighty = 0.0
            weightx = 1.0
            fill = GridBagConstraints.BOTH
          }
        }
      }
      if (searchTextOccurrencesEnabled) {
        checkbox {
          key = "searchForTextOccurrences"
          props = Checkbox(text = RefactoringBundle.getSearchForTextOccurrencesText(),
                           selected = searchTextOccurrences.value,
                           onChange = { searchTextOccurrences.value = it }).apply {
            constraints = GridBagConstraints().apply {
              insets = JBUI.insetsBottom(4)
              gridwidth = GridBagConstraints.REMAINDER
              gridx = 1
              weightx = 1.0
              fill = GridBagConstraints.BOTH
            }
          }
        }
      }
      factoriesFlags.keys.forEachIndexed { i, factory ->
        checkbox {
          key = factory.optionName
          props = Checkbox(text = factory.optionName!!,
                           selected = factoriesFlags[factory]!!,
                           onChange = { factoriesFlags[factory] = it }).apply {
            constraints = GridBagConstraints().apply {
              insets = JBUI.insetsBottom(4)
              gridwidth = if (i % 2 == 0) 1 else GridBagConstraints.REMAINDER
              gridx = i % 2
              weightx = 1.0
              fill = GridBagConstraints.BOTH
            }
          }
        }
      }
    }
  }

  NoriaDialogs.instance.show(DialogProps(
    title = RefactoringBundle.message("rename.title"),
    errorText = errorText,
    helpId = if (hasHelp) RenamePsiElementProcessor.forElement(psiElement).getHelpID(psiElement) else null,
    north = buildElement<Panel> {
      northPanel { props = Unit }
    },
    actions = listOf(
      NoriaAction(name = RefactoringBundle.message("refactor.button"),
                  role = ActionRole.Default,
                  enabled = isEnabled,
                  lambda = {
                    saveSettings(newName.value)
                    performRename(newName.value, false, { it.close(DialogWrapper.OK_EXIT_CODE) })
                  }),
      NoriaAction(name = RefactoringBundle.message("preview.button"),
                  enabled = isEnabled,
                  lambda = {
                    saveSettings(newName.value)
                    performRename(newName.value, true, { it.close(DialogWrapper.OK_EXIT_CODE) })
                  }),
      NoriaAction(name = CommonBundle.getCancelButtonText(),
                  role = ActionRole.Cancel,
                  enabled = cell { true },
                  lambda = { it.close(DialogWrapper.CANCEL_EXIT_CODE) }))))
}


