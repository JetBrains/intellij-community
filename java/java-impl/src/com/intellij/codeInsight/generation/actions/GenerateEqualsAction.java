// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateEqualsHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;

public class GenerateEqualsAction extends BaseGenerateAction implements DumbAware {
  public GenerateEqualsAction() {
    super(new GenerateEqualsHandler());
  }

  @Override
  protected PsiClass getTargetClass(Editor editor, PsiFile file) {
    final PsiClass targetClass = super.getTargetClass(editor, file);
    return targetClass == null || targetClass instanceof PsiAnonymousClass || targetClass.isEnum() ? null : targetClass;
  }
}
