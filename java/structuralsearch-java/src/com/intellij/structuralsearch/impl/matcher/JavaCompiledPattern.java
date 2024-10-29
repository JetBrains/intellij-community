// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.structuralsearch.impl.matcher.strategies.JavaMatchingStrategy;
import org.jetbrains.annotations.NotNull;

public class JavaCompiledPattern extends CompiledPattern {
  public static final String TYPED_VAR_PREFIX = "__$_";

  private boolean requestsSuperFields;
  private boolean requestsSuperMethods;
  private boolean requestsSuperInners;

  public JavaCompiledPattern() {
    setStrategy(JavaMatchingStrategy.getInstance());
  }

  @Override
  public String @NotNull [] getTypedVarPrefixes() {
    return new String[] {TYPED_VAR_PREFIX};
  }

  @Override
  public boolean isTypedVar(@NotNull final String str) {
    return str.startsWith(TYPED_VAR_PREFIX, (!str.isEmpty() && str.charAt(0) == '@') ? 1 : 0);
  }

  @Override
  public boolean isToResetHandler(@NotNull PsiElement element) {
    return !(element instanceof PsiJavaToken) &&
           !(element instanceof PsiJavaCodeReferenceElement && element.getParent() instanceof PsiAnnotation);
  }

  public boolean isRequestsSuperFields() {
    return requestsSuperFields;
  }

  public void setRequestsSuperFields(boolean requestsSuperFields) {
    this.requestsSuperFields = requestsSuperFields;
  }

  public boolean isRequestsSuperInners() {
    return requestsSuperInners;
  }

  public void setRequestsSuperInners(boolean requestsSuperInners) {
    this.requestsSuperInners = requestsSuperInners;
  }

  public boolean isRequestsSuperMethods() {
    return requestsSuperMethods;
  }

  public void setRequestsSuperMethods(boolean requestsSuperMethods) {
    this.requestsSuperMethods = requestsSuperMethods;
  }
}
