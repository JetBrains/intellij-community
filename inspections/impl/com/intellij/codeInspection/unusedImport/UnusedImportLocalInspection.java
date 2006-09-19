/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.unusedImport;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UnusedImportLocalInspection extends UnfairLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "UNUSED_IMPORT";
  public static final String DISPLAY_NAME = InspectionsBundle.message("unused.import");

  @NotNull
  public String getGroupDisplayName() {
    return GroupNames.IMPORTS_GROUP_NAME;
  }

  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }
}
