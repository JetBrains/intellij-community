// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class EditorTextFieldWithBrowseButton extends ComponentWithBrowseButton<EditorTextField> implements TextAccessor {
  public EditorTextFieldWithBrowseButton(Project project, boolean isClassAccepted) {
    this(project, isClassAccepted, JavaCodeFragment.VisibilityChecker.EVERYTHING_VISIBLE);
  }

  public EditorTextFieldWithBrowseButton(Project project, boolean isClassAccepted, JavaCodeFragment.VisibilityChecker visibilityChecker) {
    this(project, isClassAccepted, visibilityChecker, JavaFileType.INSTANCE);
  }

  public EditorTextFieldWithBrowseButton(Project project,
                                         boolean isClassAccepted,
                                         JavaCodeFragment.VisibilityChecker visibilityChecker,
                                         FileType fileType) {
    super(createEditorTextField(project, isClassAccepted, visibilityChecker, fileType), null);
  }

  private static EditorTextField createEditorTextField(Project project,
                                                       boolean isClassAccepted,
                                                       JavaCodeFragment.VisibilityChecker visibilityChecker,
                                                       FileType fileType) {
    if (project.isDefault()) {
      return new EditorTextField();
    }
    else {
      PsiElement defaultPackage = JavaPsiFacade.getInstance(project).findPackage("");
      JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
      JavaCodeFragment fragment = factory.createReferenceCodeFragment("", defaultPackage, true, isClassAccepted);
      fragment.setVisibilityChecker(visibilityChecker);
      Document document = PsiDocumentManager.getInstance(project).getDocument(fragment);
      return new EditorTextField(document, project, fileType);
    }
  }

  @Override
  public void setText(String text) {
    getChildComponent().setText(StringUtil.notNullize(text));
  }

  @NotNull
  @Override
  public String getText() {
    return getChildComponent().getText();
  }
}