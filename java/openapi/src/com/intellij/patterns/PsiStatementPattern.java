/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.patterns;

import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public class PsiStatementPattern<T extends PsiStatement, Self extends PsiStatementPattern<T, Self>> extends PsiJavaElementPattern<T, Self>{
  public PsiStatementPattern(final Class<T> aClass) {
    super(aClass);
  }

  public Self insideMethod(final PsiMethodPattern pattern) {
    return with(new PatternCondition<T>("insideMethod") {
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
