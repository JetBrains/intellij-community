// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private int myOrdinal;

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

  public int getOrdinal() {
    return myOrdinal;
  }

  public void setOrdinal(int ordinal) {
    myOrdinal = ordinal;
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
