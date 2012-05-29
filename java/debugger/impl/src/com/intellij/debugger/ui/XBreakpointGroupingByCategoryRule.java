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
import com.intellij.debugger.ui.breakpoints.BreakpointFactory;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroupingRule;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

class XBreakpointGroupingByCategoryRule<B> extends XBreakpointGroupingRule<B, XBreakpointCategoryGroup> {
  XBreakpointGroupingByCategoryRule() {
    super("XBreakpointGroupingByCategoryRule", "Type");
  }

  @Override
  public boolean isAlwaysEnabled() {
    return true;
  }

  @Override
  public XBreakpointCategoryGroup getGroup(@NotNull B b, @NotNull Collection<XBreakpointCategoryGroup> groups) {
    if (b instanceof Breakpoint) {
      final Breakpoint breakpoint = (Breakpoint)b;
      for (XBreakpointCategoryGroup group : groups) {
        if (group.getCategory().equals(breakpoint.getCategory())) {
          return group;
        }
      }
      final BreakpointFactory factory = BreakpointFactory.getInstance(breakpoint.getCategory());
      if (factory != null) {
        return new XBreakpointCategoryGroup(factory);
      }
    }
    return null;
  }
}
