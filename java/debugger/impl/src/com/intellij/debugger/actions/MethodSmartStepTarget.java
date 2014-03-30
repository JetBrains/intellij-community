/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.debugger.actions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/25/13
 */
public class MethodSmartStepTarget extends SmartStepTarget {
  private final PsiMethod myMethod;

  public MethodSmartStepTarget(@NotNull PsiMethod method, @Nullable String label, @Nullable PsiElement highlightElement, boolean needBreakpointRequest, Range<Integer> lines) {
    super(label, highlightElement, needBreakpointRequest, lines);
    myMethod = method;
  }

  @NotNull
  public PsiMethod getMethod() {
    return myMethod;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final MethodSmartStepTarget that = (MethodSmartStepTarget)o;

    if (!myMethod.equals(that.myMethod)) {
      return false;
    }

    return true;
  }

  public int hashCode() {
    return myMethod.hashCode();
  }
}
