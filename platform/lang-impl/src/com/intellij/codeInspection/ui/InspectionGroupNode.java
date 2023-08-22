// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ui;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class InspectionGroupNode extends InspectionTreeNode {
  private final @Nls String myGroup;

  InspectionGroupNode(@NotNull @Nls String group, @NotNull InspectionTreeNode parent) {
    super(parent);
    myGroup = group;
  }

  String getSubGroup() {
    return myGroup;
  }

  @Override
  public boolean appearsBold() {
    return true;
  }

  @Override
  public String getPresentableText() {
    return myGroup;
  }
}
