// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.psi;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveVisitor;
import org.jetbrains.annotations.NotNull;

public class JsonPathRecursiveElementVisitor extends JsonPathVisitor implements PsiRecursiveVisitor {
  @Override
  public void visitElement(@NotNull final PsiElement element) {
    element.acceptChildren(this);
  }
}
