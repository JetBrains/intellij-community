// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.codeInspection.ex.InspectionToolWrapper
import com.intellij.codeInspection.reference.RefElement
import com.intellij.codeInspection.reference.RefEntity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.pom.Navigatable
import com.intellij.psi.NavigatablePsiElement
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

/**
 * @author Dmitry Batkovich
 */
@ApiStatus.Internal
object InspectionResultsViewUtil {
  @JvmStatic
  fun releaseEditor(editor: Editor?) {
    if (editor != null && !editor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(editor)
    }
  }

  @JvmStatic
  fun getNavigatableForInvalidNode(node: ProblemDescriptionNode): Navigatable? {
    var element = node.element
    while (element != null && !element.isValid()) {
      element = element.getOwner()
    }
    if (element !is RefElement) return null
    val containingElement = element.psiElement
    if (containingElement !is NavigatablePsiElement || !containingElement.isValid()) return null

    val lineNumber = node.getLineNumber()
    if (lineNumber != -1) {
      val containingFile = containingElement.getContainingFile()
      if (containingFile != null) {
        val file = containingFile.getVirtualFile()
        val document = FileDocumentManager.getInstance().getDocument(file)
        if (document != null && document.getLineCount() > lineNumber) {
          return OpenFileDescriptor(containingElement.getProject(), file, lineNumber, 0)
        }
      }
    }
    return containingElement as Navigatable
  }

  @JvmStatic
  val nothingToShowTextLabel: JLabel
    get() = createLabelForText(InspectionViewNavigationPanel.getTitleText(false))

  @JvmStatic
  fun getInvalidEntityLabel(entity: RefEntity): JComponent {
    val name = entity.getName()
    return createLabelForText(InspectionsBundle.message("inspections.view.invalid.label", name))
  }

  @JvmStatic
  fun getPreviewIsNotAvailable(entity: RefEntity): JComponent {
    val name = entity.getQualifiedName()
    return createLabelForText(InspectionsBundle.message("inspections.view.no.preview.label", name))
  }

  @JvmStatic
  fun getApplyingFixLabel(wrapper: InspectionToolWrapper<*, *>): JComponent {
    return createLabelForText(InspectionsBundle.message("inspections.view.applying.quick.label", wrapper.getDisplayName()))
  }

  @JvmStatic
  fun createLabelForText(text: @Nls String): JLabel {
    val multipleSelectionLabel: JLabel = JBLabel(text)
    multipleSelectionLabel.setVerticalAlignment(SwingConstants.TOP)
    multipleSelectionLabel.setBorder(JBUI.Borders.empty(16, 12, 0, 0))
    return multipleSelectionLabel
  }
}
