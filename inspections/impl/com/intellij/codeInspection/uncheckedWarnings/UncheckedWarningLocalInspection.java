/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.codeInspection.uncheckedWarnings;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.UnfairLocalInspectionTool;
import org.jetbrains.annotations.NonNls;

/**
 * User: anna
 * Date: 17-Feb-2006
 */
public class UncheckedWarningLocalInspection extends UnfairLocalInspectionTool {
  @NonNls public static final String SHORT_NAME = "UNCHECKED_WARNING";
  public static final String DISPLAY_NAME = InspectionsBundle.message("unchecked.warning");
  @NonNls public static final String ID = "unchecked";

  public String getGroupDisplayName() {
    return "";
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @NonNls
  public String getShortName() {
    return SHORT_NAME;
  }

  @NonNls
  public String getID() {
    return ID;
  }
}
