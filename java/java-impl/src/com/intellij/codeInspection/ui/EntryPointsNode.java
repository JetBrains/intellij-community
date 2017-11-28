/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  protected void visitProblemSeverities(TObjectIntHashMap<HighlightDisplayLevel> counter) {
    //do nothing here
  }

  @Override
  public int getProblemCount(boolean allowSuppressed) {
    return 0;
  }
}
