// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.NavigatablePsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Dmitry Batkovich
 */
public final class InspectionResultsViewUtil {
  static void releaseEditor(@Nullable Editor editor) {
    if (editor != null && !editor.isDisposed()) {
      EditorFactory.getInstance().releaseEditor(editor);
    }
  }

  static @Nullable Navigatable getNavigatableForInvalidNode(ProblemDescriptionNode node) {
    RefEntity element = node.getElement();
    while (element != null && !element.isValid()) {
      element = element.getOwner();
    }
    if (!(element instanceof RefElement)) return null;
    PsiElement containingElement = ((RefElement)element).getPsiElement();
    if (!(containingElement instanceof NavigatablePsiElement) || !containingElement.isValid()) return null;

    final int lineNumber = node.getLineNumber();
    if (lineNumber != -1) {
      final PsiFile containingFile = containingElement.getContainingFile();
      if (containingFile != null) {
        final VirtualFile file = containingFile.getVirtualFile();
        final Document document = FileDocumentManager.getInstance().getDocument(file);
        if (document != null && document.getLineCount() > lineNumber) {
          return new OpenFileDescriptor(containingElement.getProject(), file, lineNumber, 0);
        }
      }
    }
    return (Navigatable)containingElement;
  }

  static @NotNull JLabel getNothingToShowTextLabel() {
    return createLabelForText(InspectionViewNavigationPanel.getTitleText(false));
  }

  static @NotNull JComponent getInvalidEntityLabel(@NotNull RefEntity entity) {
    final String name = entity.getName();
    return createLabelForText(InspectionsBundle.message("inspections.view.invalid.label", name));
  }

  public static JComponent getPreviewIsNotAvailable(@NotNull RefEntity entity) {
    final String name = entity.getQualifiedName();
    return createLabelForText(InspectionsBundle.message("inspections.view.no.preview.label", name));
  }

  static @NotNull JComponent getApplyingFixLabel(@NotNull InspectionToolWrapper wrapper) {
    return createLabelForText(InspectionsBundle.message("inspections.view.applying.quick.label", wrapper.getDisplayName()));
  }

  static @NotNull JLabel createLabelForText(@Nls String text) {
    final JLabel multipleSelectionLabel = new JBLabel(text);
    multipleSelectionLabel.setVerticalAlignment(SwingConstants.TOP);
    multipleSelectionLabel.setBorder(JBUI.Borders.empty(16, 12, 0, 0));
    return multipleSelectionLabel;
  }
}
