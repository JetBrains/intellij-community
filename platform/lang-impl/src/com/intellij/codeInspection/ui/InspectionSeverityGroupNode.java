// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.impl.SeverityRegistrar;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.project.Project;

import javax.swing.*;


public class InspectionSeverityGroupNode extends InspectionTreeNode{

  private final HighlightDisplayLevel myLevel;
  private final SeverityRegistrar mySeverityRegistrar;

  public InspectionSeverityGroupNode(final SeverityRegistrar severityRegistrar, final HighlightDisplayLevel level) {
    super(level);
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

  public String toString() {
    return StringUtil.capitalize(myLevel.toString().toLowerCase());
  }

  public HighlightDisplayLevel getSeverityLevel() {
    return myLevel;
  }

  public SeverityRegistrar getSeverityRegistrar() {
    return mySeverityRegistrar;
  }
}
