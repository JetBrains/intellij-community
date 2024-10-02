// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public final class InspectionPackageNode extends InspectionTreeNode {
  private final @NlsSafe String myPackageName;

  public InspectionPackageNode(@NotNull String packageName, InspectionTreeNode parent) {
    super(parent);
    myPackageName = packageName;
  }

  public String getPackageName() {
    return myPackageName;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.Package;
  }

  @Override
  public String getPresentableText() {
    return myPackageName;
  }
}
