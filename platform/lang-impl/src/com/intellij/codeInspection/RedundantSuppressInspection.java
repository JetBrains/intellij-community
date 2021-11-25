// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;

import javax.swing.*;

public class RedundantSuppressInspection extends RedundantSuppressInspectionBase {

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionsBundle.message("inspection.redundant.suppression.option", "@SuppressWarning(\"ALL\")"), this, "IGNORE_ALL");
  }
}
