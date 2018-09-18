// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PsiStatementPattern<T extends PsiStatement, Self extends PsiStatementPattern<T, Self>> extends PsiJavaElementPattern<T, Self>{
  public PsiStatementPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self insideMethod(final PsiMethodPattern pattern) {
    return with(new PatternCondition<T>("insideMethod") {
      @Override
      public boolean accepts(@NotNull final T t, final ProcessingContext context) {
        PsiMethod method = PsiTreeUtil.getParentOfType(t, PsiMethod.class, false, PsiMember.class);
        return method != null && pattern.accepts(method, context);
      }
    });
  }

  public Self insideMethod(StringPattern methodName, String qualifiedClassName) {
    return insideMethod(PsiJavaPatterns.psiMethod().withName(methodName).definedInClass(qualifiedClassName));
  }

  public Self insideMethod(@NotNull @NonNls String methodName, @NotNull @NonNls String qualifiedClassName) {
    return insideMethod(StandardPatterns.string().equalTo(methodName), qualifiedClassName);
  }

  public static class Capture<T extends PsiStatement> extends PsiStatementPattern<T, Capture<T>> {
    public Capture(final Class<T> aClass) {
      super(aClass);
    }

  }
}
