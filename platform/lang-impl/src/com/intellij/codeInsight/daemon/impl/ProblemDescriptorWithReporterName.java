// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ex.DynamicGroupTool;
import org.jetbrains.annotations.NotNull;

/**
 * Allows an inspection to report a problem in the name of another inspection.
 * This can be used to let one parent inspection do all the work, while the problems are reported by child inspections that
 * can be individually configured (e.g. separate severity of the problem)
 *
 * @see DynamicGroupTool
 */
public final class ProblemDescriptorWithReporterName extends ProblemDescriptorBase {
  private final String myReportingToolShortName;

  public ProblemDescriptorWithReporterName(@NotNull ProblemDescriptorBase pd, @NotNull String reportingToolShortName) {
    super(pd.getStartElement(), pd.getEndElement(), pd.getDescriptionTemplate(), pd.getFixes(),
          pd.getHighlightType(), pd.isAfterEndOfLine(), pd.getTextRangeInElement(), pd.showTooltip(), pd.isOnTheFly());
    myReportingToolShortName = reportingToolShortName;
  }

  /**
   * @return the shortName of the inspection this problem should be reported as.
   */
  public @NotNull String getReportingToolShortName() {
    return myReportingToolShortName;
  }
}
