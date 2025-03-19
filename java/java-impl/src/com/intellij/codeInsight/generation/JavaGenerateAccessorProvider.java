// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation;

import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class JavaGenerateAccessorProvider implements NotNullFunction<PsiClass, Collection<EncapsulatableClassMember>> {
  @Override
  public @NotNull Collection<EncapsulatableClassMember> fun(PsiClass psiClass) {
    if (psiClass.getLanguage() != JavaLanguage.INSTANCE) return Collections.emptyList();
    final List<EncapsulatableClassMember> result = new ArrayList<>();
    for (PsiField field : psiClass.getFields()) {
      if (!(field instanceof PsiEnumConstant)) {
        result.add(new PsiFieldMember(field));
      }
    }
    return result;
  }
}
