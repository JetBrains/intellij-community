/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.GlobalJavaInspectionContext;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefJavaElement;
import com.intellij.codeInspection.ui.InspectionNode;
import com.intellij.codeInspection.ui.InspectionResultsView;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

public class DummyEntryPointsPresentation extends UnusedDeclarationPresentation {
  private static final RefEntryPointFilter myFilter = new RefEntryPointFilter();
  private QuickFixAction[] myQuickFixActions;

  public DummyEntryPointsPresentation(@NotNull InspectionToolWrapper toolWrapper, @NotNull GlobalInspectionContextImpl context) {
    super(toolWrapper, context);
  }

  @Override
  public RefFilter getFilter() {
    return myFilter;
  }

  @Override
  public QuickFixAction[] getQuickFixes(@NotNull final RefEntity[] refElements, CommonProblemDescriptor[] allowedDescriptors) {
    if (myQuickFixActions == null) {
      myQuickFixActions = new QuickFixAction[]{new MoveEntriesToSuspicious(getToolWrapper())};
    }
    return myQuickFixActions;
  }

  @Override
  protected String getSeverityDelegateName() {
    return UnusedDeclarationInspection.SHORT_NAME;
  }

  private class MoveEntriesToSuspicious extends QuickFixAction {
    private MoveEntriesToSuspicious(@NotNull InspectionToolWrapper toolWrapper) {
      super(InspectionsBundle.message("inspection.dead.code.remove.from.entry.point.quickfix"), null, null, toolWrapper);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        final InspectionResultsView view = getInvoker(e);
        boolean permanentFound = false;
        boolean nonPermanentFound = false;
        for (RefEntity point : view.getTree().getSelectedElements()) {
          if (point instanceof RefJavaElement && ((RefJavaElement)point).isEntry()) {
            if (((RefJavaElement)point).isPermanentEntry()) {
              permanentFound = true;
            }
            else {
              nonPermanentFound = true;
            }
          }
        }

        if (permanentFound && nonPermanentFound) {
          e.getPresentation().setText(InspectionsBundle.message("inspection.dead.code.remove.user.defined.entry.point.quickfix"));
        }
        else if (nonPermanentFound || !permanentFound) {
          e.getPresentation().setEnabled(false);
        }
      }
    }

    @Override
    protected boolean applyFix(@NotNull RefEntity[] refElements) {
      final EntryPointsManager entryPointsManager =
        getContext().getExtension(GlobalJavaInspectionContext.CONTEXT).getEntryPointsManager(getContext().getRefManager());
      for (RefEntity refElement : refElements) {
        if (refElement instanceof RefJavaElement && ((RefJavaElement)refElement).isEntry() && ((RefJavaElement)refElement).isPermanentEntry()) {
          entryPointsManager.removeEntryPoint((RefElement)refElement);
        }
      }

      return true;
    }
  }

  @NotNull
  @Override
  public InspectionNode createToolNode(@NotNull GlobalInspectionContextImpl context, @NotNull InspectionNode node,
                                       @NotNull InspectionRVContentProvider provider,
                                       @NotNull InspectionTreeNode parentNode,
                                       boolean showStructure,
                                       boolean groupByStructure) {
    return node;
  }

  @Override
  @NotNull
  public DeadHTMLComposer getComposer() {
    return new DeadHTMLComposer(this);
  }

  @Override
  public boolean isDummy() {
    return true;
  }
}
