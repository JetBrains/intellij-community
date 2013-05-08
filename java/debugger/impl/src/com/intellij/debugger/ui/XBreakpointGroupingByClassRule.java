/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.debugger.ui;

import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.icons.AllIcons;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointsGroupingPriorities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;

class XBreakpointGroupingByClassRule<B> extends XBreakpointGroupingRule<B, XBreakpointClassGroup> {
  XBreakpointGroupingByClassRule() {
    super("XBreakpointGroupingByClassRule", "Group by Class");
  }

  @Override
  public boolean isAlwaysEnabled() {
    return false;
  }

  @Override
  public int getPriority() {
    return XBreakpointsGroupingPriorities.BY_CLASS;
  }

  @Override
  public XBreakpointClassGroup getGroup(@NotNull B b, @NotNull Collection<XBreakpointClassGroup> groups) {
    if (b instanceof Breakpoint) {
      final Breakpoint breakpoint = (Breakpoint)b;
      String className = breakpoint.getShortClassName();
      String packageName = breakpoint.getPackageName();
      if (className == null) {
        return null;
      }
      for (XBreakpointClassGroup group : groups) {
        if (group.getClassName().equals(className) && group.getPackageName().equals(packageName))  {
          return group;
        }
      }
      return new XBreakpointClassGroup(packageName, className);
    }
    return null;
  }

  @Nullable
  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Class;
  }
}
