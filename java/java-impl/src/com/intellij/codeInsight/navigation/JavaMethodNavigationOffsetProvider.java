// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.annotations.Nullable;


public final class JavaMethodNavigationOffsetProvider implements MethodNavigationOffsetProvider {
  @Override
  public int @Nullable [] getMethodNavigationOffsets(PsiFile file, int caretOffset) {
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
