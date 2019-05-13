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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PlatformIcons;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class XBreakpointPackageGroup extends XBreakpointGroup {
  private static final String DEFAULT_PACKAGE_NAME = DebuggerBundle.message("default.package.name");

  private final String myPackageName;

  public XBreakpointPackageGroup(String packageName) {
    myPackageName = packageName;
  }

  @Override
  public Icon getIcon(boolean isOpen) {
    return PlatformIcons.PACKAGE_ICON;
  }

  @NotNull
  @Override
  public String getName() {
    String packageName = getPackageName();
    return StringUtil.isEmpty(packageName) ? DEFAULT_PACKAGE_NAME : packageName;
  }

  @NotNull
  public String getPackageName() {
    return myPackageName;
  }
}
