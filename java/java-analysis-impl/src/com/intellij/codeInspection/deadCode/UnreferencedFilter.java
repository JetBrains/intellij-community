// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.deadCode;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.reference.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class UnreferencedFilter extends RefUnreachableFilter {
  public UnreferencedFilter(@NotNull GlobalInspectionTool tool, @NotNull GlobalInspectionContext context) {
    super(tool, context);
  }

  @Override
  public int getElementProblemCount(@NotNull RefJavaElement refElement) {
    if (refElement instanceof RefParameter) return 0;
    if (refElement.isEntry() || !((RefElementImpl)refElement).isSuspicious() || refElement.isSyntheticJSP()) return 0;

    if (!(refElement instanceof RefMethod || refElement instanceof RefClass || refElement instanceof RefField)) return 0;
    if (!((GlobalInspectionContextBase)myContext).isToCheckMember(refElement, myTool)) return 0;

    if (refElement instanceof RefField refField && !isExternallyReferenced(refElement)) {
      if (refField.isUsedForReading() && !refField.isUsedForWriting()) return 1;
      if (refField.isUsedForWriting() && !refField.isUsedForReading()) return 1;
    }

    if (refElement instanceof RefClass && ((RefClass)refElement).isAnonymous()) return 0;
    return -1;
  }

  public static boolean isExternallyReferenced(RefElement element) {
    return ContainerUtil.exists(element.getInReferences(), reference -> reference instanceof RefFile);
  }
}
