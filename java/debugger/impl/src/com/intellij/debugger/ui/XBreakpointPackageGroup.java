// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.IconManager;
import com.intellij.ui.PlatformIcons;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointGroup;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class XBreakpointPackageGroup extends XBreakpointGroup {
  private final String myPackageName;

  public XBreakpointPackageGroup(String packageName) {
    myPackageName = packageName;
  }

  @Override
  public Icon getIcon(boolean isOpen) {
    return IconManager.getInstance().getPlatformIcon(PlatformIcons.Package);
  }

  @NotNull
  @Override
  public String getName() {
    String packageName = getPackageName();
    return StringUtil.isEmpty(packageName) ? getDefaultPackageName() : packageName;
  }

  @NotNull
  @NlsSafe
  public String getPackageName() {
    return myPackageName;
  }

  @Nls
  private static String getDefaultPackageName() {
    return JavaDebuggerBundle.message("default.package.name");
  }
}
