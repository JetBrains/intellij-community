// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.reference.*;
import com.intellij.codeInspection.util.RefFilter;
import org.jetbrains.annotations.NotNull;

public class RefUnreachableFilter extends RefFilter {
  protected @NotNull GlobalInspectionTool myTool;
  protected final @NotNull GlobalInspectionContext myContext;

  public RefUnreachableFilter(@NotNull GlobalInspectionTool tool, @NotNull GlobalInspectionContext context) {
    myTool = tool;
    myContext = context;
  }

  @Override
  public int getElementProblemCount(@NotNull RefJavaElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    if (refElement.isSyntheticJSP()) return 0;
    if (!(refElement instanceof RefMethod || refElement instanceof RefClass || refElement instanceof RefField)) return 0;
    if (!((GlobalInspectionContextBase)myContext).isToCheckMember(refElement, myTool)) return 0;
    return ((RefElementImpl)refElement).isSuspicious() ? 1 : 0;
  }
}
