// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;


public class TemplateBuilderFactoryImpl extends TemplateBuilderFactory {
  @Override
  public TemplateBuilder createTemplateBuilder(@NotNull PsiElement element) {
    return new TemplateBuilderImpl(element);
  }
}
