// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ui.actions.suppress;

import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.GlobalInspectionContextImpl;
import com.intellij.codeInspection.ex.InspectionManagerEx;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ui.SuppressableInspectionTreeNode;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SequentialTask;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class SuppressActionSequentialTask implements SequentialTask {
  private static final Logger LOG = Logger.getInstance(SuppressActionSequentialTask.class);

  private final SuppressableInspectionTreeNode[] myNodesToSuppress;
  private final @NotNull SuppressIntentionAction mySuppressAction;
  private final @NotNull InspectionToolWrapper myWrapper;
  private int myCount = 0;

  public SuppressActionSequentialTask(SuppressableInspectionTreeNode @NotNull [] nodesToSuppress,
                                      @NotNull SuppressIntentionAction suppressAction,
                                      @NotNull InspectionToolWrapper wrapper) {
    myNodesToSuppress = nodesToSuppress;
    mySuppressAction = suppressAction;
    myWrapper = wrapper;
  }

  @Override
  public boolean iteration() {
    return true;
  }

  @Override
  public boolean iteration(@NotNull ProgressIndicator indicator) {
    final SuppressableInspectionTreeNode node = myNodesToSuppress[myCount++];
    indicator.setFraction((double)myCount / myNodesToSuppress.length);

    final Pair<PsiElement, CommonProblemDescriptor> content = node.getSuppressContent();
    if (content.first != null) {
      suppress(content.first, content.second, mySuppressAction, myWrapper, node);
    }

    return isDone();
  }

  @Override
  public boolean isDone() {
    return myCount > myNodesToSuppress.length - 1;
  }

  @Override
  public void prepare() {
    final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
    if (indicator != null) {
      indicator.setText(InspectionsBundle.message("inspection.action.suppress", myWrapper.getDisplayName()));
    }
  }

  private void suppress(final @NotNull PsiElement element,
                        final @Nullable CommonProblemDescriptor descriptor,
                        final @NotNull SuppressIntentionAction action,
                        @NotNull InspectionToolWrapper wrapper,
                        final @NotNull SuppressableInspectionTreeNode node) {
    if (action instanceof SuppressIntentionActionFromFix && !(descriptor instanceof ProblemDescriptor)) {
      LOG.info("local suppression fix for specific problem descriptor:  " + wrapper.getTool().getClass().getName());
    }

    final Project project = element.getProject();
    try {

      PsiElement container = null;
      if (action instanceof SuppressIntentionActionFromFix) {
        container = ((SuppressIntentionActionFromFix)action).getContainer(element);
      }
      if (container == null) {
        container = element;
      }

      if (action.isAvailable(project, null, element)) {
        ThrowableRunnable<RuntimeException> runnable = () -> action.invoke(project, null, element);
        if (action.startInWriteAction()) {
          WriteAction.run(runnable);
        }
        else {
          runnable.run();
        }
      }
      final Set<GlobalInspectionContextImpl> globalInspectionContexts =
        ((InspectionManagerEx)InspectionManager.getInstance(element.getProject())).getRunningContexts();
      for (GlobalInspectionContextImpl context : globalInspectionContexts) {
        context.resolveElement(wrapper.getTool(), container);
        if (descriptor != null) {
          context.getPresentation(wrapper).suppressProblem(descriptor);
        }
      }
    }
    catch (IncorrectOperationException e1) {
      LOG.error(e1);
    }

    node.removeSuppressActionFromAvailable(mySuppressAction);
  }
}