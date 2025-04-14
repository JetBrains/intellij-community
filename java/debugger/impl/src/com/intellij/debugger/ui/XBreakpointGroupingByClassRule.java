// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointManager;
import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointsGroupingPriorities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

final class XBreakpointGroupingByClassRule<B> extends XBreakpointGroupingRule<B, XBreakpointClassGroup> {
  XBreakpointGroupingByClassRule() {
    super("XBreakpointGroupingByClassRule", JavaDebuggerBundle.message("rule.name.group.by.class"));
  }

  @Override
  public int getPriority() {
    return XBreakpointsGroupingPriorities.BY_CLASS;
  }

  @Override
  public XBreakpointClassGroup getGroup(@NotNull B b) {
    if (b instanceof XBreakpoint) {
      Breakpoint javaBreakpoint = BreakpointManager.getJavaBreakpoint((XBreakpoint)b);
      if (javaBreakpoint == null) {
        return null;
      }
      String className = javaBreakpoint.getShortClassName();
      String packageName = javaBreakpoint.getPackageName();
      if (className == null) {
        return null;
      }
      return new XBreakpointClassGroup(packageName, className);
    }
    return null;
  }

  @Override
  public @Nullable Icon getIcon() {
    return AllIcons.Actions.GroupByClass;
  }
}
