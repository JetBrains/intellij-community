/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.util.RefFilter;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class DummyEntryPointsTool extends FilteringInspectionTool {
  private static final RefEntryPointFilter myFilter = new RefEntryPointFilter();
  private QuickFixAction[] myQuickFixActions;

  public DummyEntryPointsTool(@NotNull UnusedDeclarationInspection owner) {
    initialize(owner.getContext());
  }

  @Override
  public RefFilter getFilter() {
    return myFilter;
  }

  @Override
  public void runInspection(@NotNull AnalysisScope scope, @NotNull final InspectionManager manager) {}

  @Override
  public void exportResults(@NotNull Element parentNode, @NotNull RefEntity refEntity) {
  }

  @Override
  @NotNull
  public JobDescriptor[] getJobDescriptors(@NotNull GlobalInspectionContext globalInspectionContext) {
    return JobDescriptor.EMPTY_ARRAY;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.dead.code.entry.points.display.name");
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public String getShortName() {
    return "";
  }

  @Override
  @NotNull
  public HTMLComposerImpl getComposer() {
    return new DeadHTMLComposer(this);
  }

  @Override
  public QuickFixAction[] getQuickFixes(@NotNull final RefEntity[] refElements) {
    if (myQuickFixActions == null) {
      myQuickFixActions = new QuickFixAction[]{new MoveEntriesToSuspicious()};
    }
    return myQuickFixActions;
  }

  private class MoveEntriesToSuspicious extends QuickFixAction {
    private MoveEntriesToSuspicious() {
      super(InspectionsBundle.message("inspection.dead.code.remove.from.entry.point.quickfix"), null, null, DummyEntryPointsTool.this);
    }

    @Override
    protected boolean applyFix(RefElement[] refElements) {
      final EntryPointsManager entryPointsManager =
        getContext().getExtension(GlobalJavaInspectionContextImpl.CONTEXT).getEntryPointsManager(getContext().getRefManager());
      for (RefElement refElement : refElements) {
        entryPointsManager.removeEntryPoint(refElement);
      }

      return true;
    }
  }
}
