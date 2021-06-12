// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.reference.RefEntity;


public interface ReportedProblemFilter {
  boolean shouldReportProblem(RefEntity refElement, CommonProblemDescriptor[] descriptors);
}
