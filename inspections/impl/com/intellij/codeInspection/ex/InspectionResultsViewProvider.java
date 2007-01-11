/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */

/*
 * User: anna
 * Date: 10-Jan-2007
 */
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import org.jetbrains.annotations.Nullable;

public interface InspectionResultsViewProvider {

  boolean hasReportedProblems(final InspectionTool tool);

  InspectionTreeNode [] getContents(final InspectionTool tool);

  @Nullable
  QuickFixAction[] getQuickFixes(final InspectionTool tool, final InspectionTree tree);

}