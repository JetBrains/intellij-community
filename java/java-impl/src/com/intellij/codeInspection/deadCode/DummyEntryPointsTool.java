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
  private RefEntryPointFilter myFilter;
  private final UnusedDeclarationInspection myOwner;
  private QuickFixAction[] myQuickFixActions;

  public DummyEntryPointsTool(UnusedDeclarationInspection owner) {
    myOwner = owner;
  }

  public RefFilter getFilter() {
    if (myFilter == null) {
      myFilter = new RefEntryPointFilter();
    }
    return myFilter;
  }

  public void runInspection(AnalysisScope scope, final InspectionManager manager) {}

  public void exportResults(Element parentNode) {}

  @NotNull
  public JobDescriptor[] getJobDescriptors() {
    return new JobDescriptor[0];
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("inspection.dead.code.entry.points.display.name");
  }

  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public String getShortName() {
    return "";
  }

  public HTMLComposerImpl getComposer() {
    return new DeadHTMLComposer(this);
  }

  public GlobalInspectionContextImpl getContext() {
    return myOwner.getContext();
  }

  public QuickFixAction[] getQuickFixes(final RefEntity[] refElements) {
    if (myQuickFixActions == null) {
      myQuickFixActions = new QuickFixAction[]{new MoveEntriesToSuspicious()};
    }
    return myQuickFixActions;
  }

  private class MoveEntriesToSuspicious extends QuickFixAction {
    private MoveEntriesToSuspicious() {
      super(InspectionsBundle.message("inspection.dead.code.remove.from.entry.point.quickfix"), null, null, DummyEntryPointsTool.this);
    }

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
