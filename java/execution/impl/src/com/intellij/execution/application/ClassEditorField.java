// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.application;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.ui.EditorTextField;

public class ClassEditorField extends EditorTextField {

  public static ClassEditorField createClassField(Project project) {
    PsiElement defaultPackage = JavaPsiFacade.getInstance(project).findPackage("");
    JavaCodeFragmentFactory factory = JavaCodeFragmentFactory.getInstance(project);
    JavaCodeFragment fragment = factory.createReferenceCodeFragment("", defaultPackage, true, true);
    fragment.setVisibilityChecker(JavaCodeFragment.VisibilityChecker.PROJECT_SCOPE_VISIBLE);
    Document document = PsiDocumentManager.getInstance(project).getDocument(fragment);
    return new ClassEditorField(document, project, JavaFileType.INSTANCE);
  }

  private ClassEditorField(Document document, Project project, FileType fileType) {
    super(document, project, fileType);
  }
}
