// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deadCode;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInspection.GlobalJavaInspectionContext;
import com.intellij.codeInspection.ex.*;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefJavaElement;
import com.intellij.codeInspection.ui.InspectionTree;
import com.intellij.codeInspection.ui.InspectionTreeModel;
import com.intellij.codeInspection.ui.InspectionTreeNode;
import com.intellij.codeInspection.ui.RefElementNode;
import com.intellij.codeInspection.util.RefFilter;
import com.intellij.openapi.actionSystem.AnActionEvent;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DummyEntryPointsPresentation extends UnusedDeclarationPresentation {
  private static final RefEntryPointFilter myFilter = new RefEntryPointFilter();
  private QuickFixAction[] myQuickFixActions;

  public DummyEntryPointsPresentation(@NotNull InspectionToolWrapper toolWrapper, @NotNull GlobalInspectionContextImpl context) {
    super(toolWrapper, context);
  }

  @NotNull
  @Override
  public RefFilter getFilter() {
    return myFilter;
  }

  @Override
  public QuickFixAction @NotNull [] getQuickFixes(RefEntity @NotNull ... refElements) {
    if (myQuickFixActions == null) {
      myQuickFixActions = new QuickFixAction[]{new MoveEntriesToSuspicious(getToolWrapper())};
    }
    return myQuickFixActions;
  }

  @Override
  public @NotNull RefElementNode createRefNode(@Nullable RefEntity entity,
                                               @NotNull InspectionTreeModel model,
                                               @NotNull InspectionTreeNode parent) {
    return new UnusedDeclarationRefElementNode(entity, this, parent) {
      @Override
      protected void visitProblemSeverities(@NotNull Object2IntMap<HighlightDisplayLevel> counter) {
        // do nothing
      }
    };
  }

  @Override
  protected String getSeverityDelegateName() {
    return UnusedDeclarationInspectionBase.SHORT_NAME;
  }

  private final class MoveEntriesToSuspicious extends QuickFixAction {
    private MoveEntriesToSuspicious(@NotNull InspectionToolWrapper toolWrapper) {
      super(AnalysisBundle.message("inspection.dead.code.remove.user.defined.entry.point.quickfix"), null, null, toolWrapper);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      super.update(e);
      if (e.getPresentation().isEnabled()) {
        for (RefEntity point : InspectionTree.getSelectedRefElements(e)) {
          if (point instanceof RefJavaElement &&
              ((RefJavaElement)point).isEntry() && 
              ((RefJavaElement)point).isPermanentEntry()) {
            return;
          }
        }

        e.getPresentation().setEnabled(false);
      }
    }

    @Override
    protected boolean applyFix(RefEntity @NotNull [] refElements) {
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
  public void patchToolNode(@NotNull InspectionTreeNode node,
                            @NotNull InspectionRVContentProvider provider,
                            boolean showStructure,
                            boolean groupByStructure) {
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
