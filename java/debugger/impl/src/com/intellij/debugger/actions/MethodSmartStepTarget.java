// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.JVMNameUtil;
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

  public @NotNull PsiMethod getMethod() {
    return myMethod;
  }

  @Override
  public Icon getIcon() {
    return myMethod.getIcon(0);
  }

  @Override
  public @NotNull String getPresentation() {
    String label = getLabel();
    String formatted = PsiFormatUtil.formatMethod(
      myMethod,
      PsiSubstitutor.EMPTY,
      PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
      PsiFormatUtilBase.SHOW_TYPE,
      999
    );
    return label != null ? label + formatted : formatted;
  }

  public int getOrdinal() {
    return myOrdinal;
  }

  public void setOrdinal(int ordinal) {
    myOrdinal = ordinal;
  }

  @Override
  public @Nullable String getClassName() {
    return JVMNameUtil.getClassVMName(myMethod.getContainingClass());
  }
}
