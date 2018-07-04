/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.util.Range;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
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

  @Override
  public Icon getIcon() {
    return myMethod.getIcon(0);
  }

  @NotNull
  @Override
  public String getPresentation() {
    String label = getLabel();
    String formatted = PsiFormatUtil.formatMethod(
      myMethod,
      PsiSubstitutor.EMPTY,
      PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
      PsiFormatUtilBase.SHOW_TYPE,
      999
    );
    return label != null? label + formatted : formatted;
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
