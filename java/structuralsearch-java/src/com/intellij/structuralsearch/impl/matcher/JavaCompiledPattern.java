// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.impl.matcher;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.structuralsearch.impl.matcher.strategies.JavaMatchingStrategy;
import org.jetbrains.annotations.NotNull;

/**
* @author Eugene.Kudelevsky
*/
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
    if (str.isEmpty()) return false;
    if (str.charAt(0)=='@') {
      return str.regionMatches(1,TYPED_VAR_PREFIX,0,TYPED_VAR_PREFIX.length());
    } else {
      return str.startsWith(TYPED_VAR_PREFIX);
    }
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
