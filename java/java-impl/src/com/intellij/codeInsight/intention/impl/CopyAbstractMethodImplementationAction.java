// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.NotNull;


public final class CopyAbstractMethodImplementationAction extends ImplementAbstractMethodAction {
  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("intention.family.copy.abstract.method.implementation");
  }

  @Override
  protected String getIntentionName(final PsiMethod method) {
    return JavaBundle.message("copy.abstract.method.intention.name", method.getName());
  }

  @Override
  protected boolean isAvailable(final MyElementProcessor processor) {
    return processor.hasMissingImplementations() && processor.hasExistingImplementations();
  }

  @Override
  protected void invokeHandler(final Project project, final Editor editor, final PsiMethod method) {
    new CopyAbstractMethodImplementationHandler(project, editor, method).invoke();
  }
}
