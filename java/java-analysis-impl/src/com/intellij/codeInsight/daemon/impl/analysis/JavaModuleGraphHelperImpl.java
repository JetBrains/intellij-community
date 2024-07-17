// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.psi.JavaModuleGraphHelper;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaModule;
import org.jetbrains.annotations.Nullable;

public class JavaModuleGraphHelperImpl extends JavaModuleGraphHelper {
  @Override
  public @Nullable PsiJavaModule findDescriptorByElement(@Nullable PsiElement element) {
    return JavaModuleGraphUtil.findDescriptorByElement(element);
  }
}