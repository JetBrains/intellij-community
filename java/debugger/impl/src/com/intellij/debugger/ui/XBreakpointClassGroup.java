/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.debugger.DebuggerBundle;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class XBreakpointClassGroup extends XBreakpointGroup {
  private static final String DEFAULT_PACKAGE_NAME = DebuggerBundle.message("default.package.name");

  private final String myPackageName;
  private final String myClassName;

  public XBreakpointClassGroup(@Nullable String packageName, String className) {
    myPackageName = packageName != null ? packageName : DEFAULT_PACKAGE_NAME;
    myClassName = className;
  }

  @Override
  public Icon getIcon(boolean isOpen) {
    return PlatformIcons.CLASS_ICON;
  }

  @NotNull
  @Override
  public String getName() {
    return getClassName();
  }

  @NotNull
  public String getPackageName() {
    return myPackageName;
  }

  @NotNull
  public String getClassName() {
    return myClassName;
  }
}
