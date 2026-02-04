// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename

import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.refactoring.RefactoringBundle
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel


/**
 * Renames files and directories in dumb mode (and only in dumb mode)
 * In smart mode does not rename anything, so other rename processors can work instead
 *
 * Should only be used when rename is handled by [FileDumbRenameHandler]
 *
 * Unfortunatelly, [RenamePsiElementProcessorBase.forPsiElement], [RenamePsiElementProcessor.forElement] and [RenamePsiElementProcessor.allForElement]
 * are executed during refactoring, so we need to add this processor to the extension point
 */
@ApiStatus.Internal
class RenamePsiFileDumbProcessor : RenamePsiElementProcessor(), DumbAware {
  override fun canProcessElement(element: PsiElement): Boolean {
    return Registry.`is`("rename.files.in.dumb.mode.enable") &&
           DumbService.isDumb(element.project) && element is PsiFileSystemItem
  }

  override fun isToSearchForTextOccurrences(element: PsiElement): Boolean {
    return false
  }

  override fun createDialog(
    project: Project,
    element: PsiElement,
    nameSuggestionContext: PsiElement?,
    editor: Editor?,
  ): RenameRefactoringDialog {
    return PsiFileDumbRenameDialog(project, element, nameSuggestionContext, editor)
  }
}


/**
 * This dialog should only be created by [RenamePsiFileDumbProcessor] in dumb mode
 */
private class PsiFileDumbRenameDialog(project: Project, element: PsiElement, nameSuggestionContext: PsiElement?, editor: Editor?) :
  RenameWithOptionalReferencesDialog(project, element, nameSuggestionContext, editor), DumbAware {
  override fun createRenameProcessor(newName: String): RenameProcessor {
    return FileRenameRefactoringProcessor(
      project, psiElement, newName, getRefactoringScope(), isSearchInComments, isSearchInNonJavaFiles
    )
  }

  override fun getLabelText(): @NlsContexts.Label String {
    return RefactoringBundle.message("rename.0.to", fullName)
  }

  override fun createNorthPanel(): JComponent? {
    val panel = super.createNorthPanel()
    if (panel == null) return null


    val warningLabel = JBLabel().apply {
      icon = AllIcons.General.Warning
      text = RefactoringBundle.message("rename.dumb.mode.warning")
      horizontalAlignment = JBLabel.LEFT
      foreground = JBUI.CurrentTheme.BigPopup.searchFieldGrayForeground()
    }
    val wrapper = JPanel(BorderLayout()).apply {
      add(panel, BorderLayout.CENTER)
      add(warningLabel, BorderLayout.SOUTH)
      border = JBUI.Borders.emptyBottom(8)
    }
    return wrapper
  }

  override fun createCheckboxes(panel: JPanel, gbConstraints: GridBagConstraints) {
    super.createCheckboxes(panel, gbConstraints)
    panel.components.filterIsInstance<JCheckBox>().forEach { checkbox ->
      checkbox.isSelected = false
      checkbox.isEnabled = false
    }
  }

  override fun getSearchForReferences(): Boolean {
    return false
  }

  override fun setSearchForReferences(value: Boolean) {
  }

  override fun createSearchScopePanel(): JComponent? {
    return super.createSearchScopePanel()?.apply {
      components.forEach { it.isEnabled = false }
    }
  }

  override fun hasPreviewButton(): Boolean {
    return true
  }

  override fun isToSearchForTextOccurrencesForRename(): Boolean {
    return false
  }

  override fun isToSearchInCommentsForRename(): Boolean {
    return false
  }
}
