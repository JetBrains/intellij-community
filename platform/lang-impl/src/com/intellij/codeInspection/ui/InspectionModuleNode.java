// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class InspectionModuleNode extends InspectionTreeNode {
  @NotNull
  private final Module myModule;

  public InspectionModuleNode(@NotNull Module module, @NotNull InspectionTreeNode parent) {
    super(parent);
    myModule = module;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return myModule.isDisposed() ? null : ModuleType.get(myModule).getIcon();
  }

  @NlsSafe
  public String getName() {
    return myModule.getName();
  }

  @Override
  public String getPresentableText() {
    return getName();
  }
}
