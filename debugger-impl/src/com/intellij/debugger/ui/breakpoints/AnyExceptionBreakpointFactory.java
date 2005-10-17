/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class AnyExceptionBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project) {
    return new AnyExceptionBreakpoint(project);
  }

  public Icon getIcon() {
    return ExceptionBreakpoint.ICON;
  }

  public Icon getDisabledIcon() {
    return ExceptionBreakpoint.DISABLED_ICON;
  }

  public @Nullable BreakpointPanel createBreakpointPanel(Project project, DialogWrapper parentDialog) {
    return null;
  }

  public String getBreakpointCategory() {
    return AnyExceptionBreakpoint.ANY_EXCEPTION_BREAKPOINT;
  }

  public String getComponentName() {
    return "AnyExceptionBreakpointFactory";
  }
}
