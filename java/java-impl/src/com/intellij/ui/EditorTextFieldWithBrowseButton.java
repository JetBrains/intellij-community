// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class EditorTextFieldWithBrowseButton extends ComponentWithBrowseButton<EditorTextField> implements TextAccessor {
  public EditorTextFieldWithBrowseButton(Project project, boolean isClassAccepted) {
    this(project, isClassAccepted, JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
  }

  public EditorTextFieldWithBrowseButton(Project project,
                                         boolean isClassAccepted,
                                         final JavaCodeFragment.VisibilityChecker visibilityChecker) {
    super(createEditorTextField(project, isClassAccepted, visibilityChecker), null);
  }

  public EditorTextFieldWithBrowseButton(Project project,
                                         boolean isClassAccepted,
                                         final JavaCodeFragment.VisibilityChecker visibilityChecker,
                                         FileType fileType) {
    super(createEditorTextField(project, isClassAccepted, visibilityChecker, fileType), null);
  }

  private static EditorTextField createEditorTextField(Project project,
                                                       boolean isClassAccepted,
                                                       JavaCodeFragment.VisibilityChecker visibilityChecker) {
    return createEditorTextField(project, isClassAccepted, visibilityChecker, StdFileTypes.JAVA);
  }

  private static EditorTextField createEditorTextField(Project project,
                                                       boolean isClassAccepted,
                                                       JavaCodeFragment.VisibilityChecker visibilityChecker,
                                                       final FileType fileType) {
    if (project.isDefault()) return new EditorTextField();
    return new EditorTextField(createDocument("", project, isClassAccepted,
                                             visibilityChecker), project, fileType);
  }

  private static Document createDocument(final String text,
                                         Project project,
                                         boolean isClassesAccepted,
                                         JavaCodeFragment.VisibilityChecker visibilityChecker) {
    PsiElement defaultPackage = JavaPsiFacade.getInstance(project).findPackage("");
    final JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    final JavaCodeFragment fragment = factory.createReferenceCodeFragment(text, defaultPackage, true, isClassesAccepted);
    fragment.setVisibilityChecker(visibilityChecker);
    return PsiDocumentManager.getInstance(project).getDocument(fragment);
  }

  @Override
  public void setText(String text) {
    if (text == null) text = "";
    getChildComponent().setText(text);
  }

  @Override
  @NotNull
  public String getText() {
    return getChildComponent().getText();
  }
}
