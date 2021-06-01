// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ide.actions;

import com.intellij.openapi.editor.EditorModificationUtil;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;


public interface QualifiedNameProvider {
  ExtensionPointName<QualifiedNameProvider> EP_NAME = ExtensionPointName.create("com.intellij.qualifiedNameProvider");

  @Nullable
  PsiElement adjustElementToCopy(PsiElement element);

  @Nullable
  String getQualifiedName(PsiElement element);

  @Nullable
  PsiElement qualifiedNameToElement(final String fqn, final Project project);

  default void insertQualifiedName(final String fqn, final PsiElement element, final Editor editor, final Project project) {
    EditorModificationUtil.insertStringAtCaret(editor, fqn);
  }
}
