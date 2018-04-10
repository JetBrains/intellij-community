/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.*;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaMethodNavigationOffsetProvider implements MethodNavigationOffsetProvider {
  @Override
  @Nullable
  public int[] getMethodNavigationOffsets(PsiFile file, int caretOffset) {
    if (file instanceof PsiJavaFile) {
      return MethodUpDownUtil.offsetsFromElements(SyntaxTraverser.psiTraverser(file).filter(e -> shouldStopAt(e)).toList());
    }
    return null;
  }

  private static boolean shouldStopAt(PsiElement e) {
    if (e instanceof PsiMethod || e instanceof PsiClass && !(e instanceof PsiTypeParameter)) return true;
    if (e instanceof PsiField) return Registry.is("ide.structural.navigation.visit.fields");
    return e instanceof PsiJavaToken && e.getParent() instanceof PsiClass && e.textMatches("}");
  }
}
