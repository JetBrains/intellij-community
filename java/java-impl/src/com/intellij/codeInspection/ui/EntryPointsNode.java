// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.icons.AllIcons;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class EntryPointsNode extends InspectionNode {
  private volatile boolean myExcluded;

  public EntryPointsNode(@NotNull InspectionToolWrapper dummyWrapper,
                         @NotNull GlobalInspectionContextImpl context,
                         @NotNull InspectionTreeNode parent) {
    super(dummyWrapper, context.getCurrentProfile(), parent);
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.EntryPoints;
  }

  @Override
  public @NotNull String getTailText() {
    return "";
  }

  @Override
  protected void visitProblemSeverities(@NotNull Object2IntMap<HighlightDisplayLevel> counter) {
    //do nothing here
  }

  @Override
  public boolean isExcluded() {
    return myExcluded;
  }

  @Override
  public void excludeElement() {
    myExcluded = true;
  }

  @Override
  public void amnestyElement() {
    myExcluded = false;
  }
}
