// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInsight.template;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class TemplateBuilderFactory {
  public static TemplateBuilderFactory getInstance() {
    return ServiceManager.getService(TemplateBuilderFactory.class);
  }

  public abstract TemplateBuilder createTemplateBuilder(@NotNull PsiElement element);
}
