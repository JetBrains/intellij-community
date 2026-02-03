// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.ui;

import com.intellij.codeInspection.InspectionsBundle;

public final class InspectionRootNode extends InspectionTreeNode {
  InspectionRootNode(InspectionTreeModel model) {
    super(null);
  }

  @Override
  public String getPresentableText() {
    return InspectionsBundle.message("inspection.results");
  }

  @Override
  protected boolean doesNeedInternProblemLevels() {
    return true;
  }

  @Override
  public boolean appearsBold() {
    return true;
  }
}
