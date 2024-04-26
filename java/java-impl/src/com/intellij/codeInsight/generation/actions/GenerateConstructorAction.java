// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateConstructorHandler;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiImplicitClass;
import org.jetbrains.annotations.VisibleForTesting;

/**
 * Action group which contains Generate... actions
 * Available in the Java code editor context only
 */
public class GenerateConstructorAction extends BaseGenerateAction implements DumbAware {
  public GenerateConstructorAction() {
    super(new GenerateConstructorHandler());
  }

  @VisibleForTesting
  @Override
  public boolean isValidForClass(final PsiClass targetClass) {
    return super.isValidForClass(targetClass) && !(targetClass instanceof PsiAnonymousClass) && !(targetClass instanceof PsiImplicitClass);
  }
}