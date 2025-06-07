// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointsGroupingPriorities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

public final class XBreakpointGroupingByPackageRule<B> extends XBreakpointGroupingRule<B, XBreakpointPackageGroup> {

  private XBreakpointGroupingByPackageRule() {
    super("XBreakpointGroupingByPackageRule", JavaDebuggerBundle.message("rule.name.group.by.package"));
  }

  @Override
  public int getPriority() {
    return XBreakpointsGroupingPriorities.BY_PACKAGE;
  }

  @Override
  public XBreakpointPackageGroup getGroup(@NotNull B breakpoint) {
    String packageName = null;
    if (breakpoint instanceof XBreakpoint) {
      Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint((XBreakpoint)breakpoint);
      if (javaBreakpoint != null) {
        packageName = javaBreakpoint.getPackageName();
      }
    }
    if (packageName == null) {
      return null;
    }
    return new XBreakpointPackageGroup(packageName);
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.Actions.GroupByPackage;
  }
}
