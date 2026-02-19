// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.generation.GenerateProviderMethodHandler;
import com.intellij.codeInspection.jigsaw.JigsawUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiClass;

public class GenerateProviderMethodAction extends BaseGenerateAction implements DumbAware {
  public GenerateProviderMethodAction() {
    super(new GenerateProviderMethodHandler());
  }

  @Override
  protected boolean isValidForClass(PsiClass targetClass) {
    if (!super.isValidForClass(targetClass)) return false;
    if (!targetClass.isWritable()) return false;
    return JigsawUtil.checkProviderMethodAccessible(targetClass);
  }
}
