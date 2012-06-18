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
import com.intellij.openapi.util.Key;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import com.intellij.xdebugger.impl.breakpoints.ui.grouping.XBreakpointTypeGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Created with IntelliJ IDEA.
 * User: zajac
 * Date: 23.05.12
 * Time: 16:22
 * To change this template use File | Settings | File Templates.
 */
public class XBreakpointCategoryGroup extends XBreakpointGroup {
  private Key<? extends Breakpoint> myCategory;
  private Icon myIcon;
  private final String myName;

  public XBreakpointCategoryGroup(BreakpointFactory factory) {
    myCategory = factory.getBreakpointCategory();
    myIcon = factory.getIcon();
    final String name = factory.getDisplayName();
    myName = name != null ? name : "UNKNOWN";
  }

  public Key<? extends Breakpoint> getCategory() {
    return myCategory;
  }

  @Override
  public Icon getIcon(boolean isOpen) {
    return myIcon;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public int compareTo(XBreakpointGroup o) {
    if (o instanceof XBreakpointTypeGroup) {
      return -1;
    }
    if (o instanceof XBreakpointCategoryGroup) {
      return getFactoryIndex() - ((XBreakpointCategoryGroup)o).getFactoryIndex();
    }
    return super.compareTo(o);
  }

  private int getFactoryIndex() {
    BreakpointFactory[] breakpointFactories = BreakpointFactory.getBreakpointFactories();
    for (int i = 0; i < breakpointFactories.length; ++i) {
      if (breakpointFactories[i].getBreakpointCategory().equals(myCategory)) {
        return i;
      }
    }
    return -1;
  }
}
