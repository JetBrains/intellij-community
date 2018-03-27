// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui;

import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class InspectionGroupNode extends InspectionTreeNode {
  InspectionGroupNode(@NotNull String subGroup) {
    super(subGroup);
  }

  String getSubGroup() {
    return (String) getUserObject();
  }

  @Override
  public boolean appearsBold() {
    return true;
  }
}
