/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.codeInspection.deadCode;

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

  @NotNull
  @Override
  public QuickFixAction[] getQuickFixes(@NotNull RefEntity... refElements) {
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
      super(InspectionsBundle.message("inspection.dead.code.remove.user.defined.entry.point.quickfix"), null, null, toolWrapper);
    }

    @Override
    public void update(AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        final InspectionResultsView view = getInvoker(e);
        boolean permanentFound = false;
        for (RefEntity point : view.getTree().getSelectedElements()) {
          if (point instanceof RefJavaElement && ((RefJavaElement)point).isEntry()) {
            if (((RefJavaElement)point).isPermanentEntry()) {
              permanentFound = true;
              break;
            }
          }
        }

        if (!permanentFound) {
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

  @Override
  public void createToolNode(@NotNull GlobalInspectionContextImpl context, @NotNull InspectionNode node,
                                       @NotNull InspectionRVContentProvider provider,
                                       @NotNull InspectionTreeNode parentNode,
                                       boolean showStructure,
                                       boolean groupByStructure) {
    myToolNode = node;
  }

  @Override
  protected boolean skipEntryPoints(RefJavaElement refElement) {
    return false;
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
