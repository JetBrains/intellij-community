// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class InspectionSeverityGroupNode extends InspectionTreeNode{
  private final HighlightDisplayLevel myLevel;
  private final SeverityRegistrar mySeverityRegistrar;

  public InspectionSeverityGroupNode(@NotNull SeverityRegistrar severityRegistrar,
                                     @NotNull HighlightDisplayLevel level,
                                     @NotNull InspectionTreeNode parent) {
    super(parent);
    myLevel = level;
    mySeverityRegistrar = severityRegistrar;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return myLevel.getIcon();
  }

  @Override
  public boolean appearsBold() {
    return true;
  }

  @Override
  public String getPresentableText() {
    return myLevel.getSeverity().getDisplayCapitalizedName();
  }

  public HighlightDisplayLevel getSeverityLevel() {
    return myLevel;
  }

  public SeverityRegistrar getSeverityRegistrar() {
    return mySeverityRegistrar;
  }
}
