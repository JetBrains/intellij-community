// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.ui;

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.deadCode.DummyEntryPointsEP;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.icons.AllIcons;
import gnu.trove.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author max
 */
public class EntryPointsNode extends InspectionNode {
  public EntryPointsNode(@NotNull GlobalInspectionContextImpl context) {
    super(createDummyWrapper(context), context.getCurrentProfile());
  }

  private static InspectionToolWrapper createDummyWrapper(@NotNull GlobalInspectionContextImpl context) {
    InspectionToolWrapper toolWrapper = new GlobalInspectionToolWrapper(new DummyEntryPointsEP());
    toolWrapper.initialize(context);
    return toolWrapper;
  }

  @Override
  public Icon getIcon(boolean expanded) {
    return AllIcons.Nodes.EntryPoints;
  }

  @Nullable
  @Override
  public String getTailText() {
    return "";
  }

  @Override
  protected void visitProblemSeverities(@NotNull TObjectIntHashMap<HighlightDisplayLevel> counter) {
    //do nothing here
  }

  @Override
  public int getProblemCount(boolean allowSuppressed) {
    return 0;
  }
}
