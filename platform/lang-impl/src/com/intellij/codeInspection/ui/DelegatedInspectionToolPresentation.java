// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionProblemConsumer;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.reference.RefEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DelegatedInspectionToolPresentation extends DefaultInspectionToolPresentation {

  @NotNull
  private final InspectionProblemConsumer myDelegate;

  public DelegatedInspectionToolPresentation(@NotNull InspectionToolWrapper toolWrapper,
                                             @NotNull GlobalInspectionContextImpl context,
                                             @NotNull InspectionProblemConsumer delegate) {
    super(toolWrapper, context);
    myDelegate = delegate;
  }

  @Override
  public void addProblemElement(@Nullable RefEntity refElement,
                                boolean filterSuppressed,
                                CommonProblemDescriptor @NotNull ... descriptors) {
    if (refElement == null || descriptors.length == 0) {
      return;
    }
    ReportedProblemFilter filter = myContext.getReportedProblemFilter();
    if (filter != null && !filter.shouldReportProblem(refElement, descriptors)) {
      return;
    }
    exportResults(descriptors, refElement, (element) -> myDelegate.consume(element, myToolWrapper));
  }
}
