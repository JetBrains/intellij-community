// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.codeInspection.reference.RefElement;
import com.intellij.codeInspection.reference.RefEntity;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.codeInspection.ui.InspectionToolPresentation;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModCommandExecutor.BatchExecutionResult;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class LocalQuickFixWrapper extends QuickFixAction {
  private final QuickFix<?> myFix;

  public LocalQuickFixWrapper(@NotNull QuickFix<?> fix, @NotNull InspectionToolWrapper<?,?> toolWrapper) {
    super(StringUtil.escapeMnemonics(fix.getName()),
          fix instanceof Iconable ? ((Iconable)fix).getIcon(0) : null, null, toolWrapper);
    myFix = fix;
  }

  public void setText(@NotNull @NlsActions.ActionText String text) {
    getTemplatePresentation().setText(text);
  }

  @Override
  protected boolean isProblemDescriptorsAcceptable() {
    return true;
  }

  public @NotNull QuickFix<?> getFix() {
    return myFix;
  }

  private @Nullable QuickFix<?> getWorkingQuickFix(QuickFix<?> @NotNull [] fixes) {
    for (QuickFix<?> fix : fixes) {
      if (fix.getFamilyName().equals(myFix.getFamilyName())) {
        return fix;
      }
    }
    return null;
  }

  @Override
  protected boolean applyFix(RefEntity @NotNull [] refElements) {
    return true;
  }

  @Override
  protected @NotNull BatchExecutionResult applyFix(final @NotNull Project project,
                                                   final @NotNull GlobalInspectionContextImpl context,
                                                   final CommonProblemDescriptor @NotNull [] descriptors,
                                                   final @NotNull Set<? super PsiElement> ignoredElements) {
    if (myFix instanceof BatchQuickFix) {
      final List<PsiElement> collectedElementsToIgnore = new ArrayList<>();
      final Runnable refreshViews = () -> {
        DaemonCodeAnalyzer.getInstance(project).restart();
        for (CommonProblemDescriptor descriptor : descriptors) {
          ignore(ignoredElements, descriptor, getWorkingQuickFix(descriptor.getFixes()) != null, context);
        }

        final RefManager refManager = context.getRefManager();
        final RefElement[] refElements = new RefElement[collectedElementsToIgnore.size()];
        for (int i = 0, collectedElementsToIgnoreSize = collectedElementsToIgnore.size(); i < collectedElementsToIgnoreSize; i++) {
          refElements[i] = refManager.getReference(collectedElementsToIgnore.get(i));
        }

        removeElements(refElements, project, myToolWrapper);
      };
      Runnable fixApplicator = () -> ((BatchQuickFix)myFix).applyFix(project, descriptors, collectedElementsToIgnore, refreshViews);
      if (myFix.startInWriteAction()) {
        WriteCommandAction.writeCommandAction(project).run(() -> {
          fixApplicator.run();
        });
      } else {
        fixApplicator.run();
      }

      return ModCommandExecutor.Result.SUCCESS;
    }

    boolean restart = false;
    BatchExecutionResult result = ModCommandExecutor.Result.NOTHING;
    for (CommonProblemDescriptor descriptor : descriptors) {
      if (descriptor == null) continue;
      final QuickFix<?>[] fixes = descriptor.getFixes();
      if (fixes != null) {
        final QuickFix fix = getWorkingQuickFix(fixes);
        if (fix != null) {
          if (fix instanceof ModCommandQuickFix modCommandQuickFix) {
            ProblemDescriptor problemDescriptor = (ProblemDescriptor)descriptor;
            ModCommand command = modCommandQuickFix.perform(project, problemDescriptor);
            result = result.compose(
              ModCommandExecutor.getInstance().executeInBatch(ActionContext.from(problemDescriptor), command));
          } else {
            //CCE here means QuickFix was incorrectly inherited, is there a way to signal (plugin) it is wrong?
            fix.applyFix(project, descriptor);
            result = ModCommandExecutor.Result.SUCCESS;
          }
          restart = true;
          ignore(ignoredElements, descriptor, true, context);
        }
      }
    }
    if (restart) {
      DaemonCodeAnalyzer.getInstance(project).restart();
    }
    return result;
  }

  @Override
  protected boolean startInWriteAction() {
    return myFix.startInWriteAction();
  }

  @Override
  protected void performFixesInBatch(@NotNull Project project,
                                     @NotNull List<CommonProblemDescriptor[]> descriptors,
                                     @NotNull GlobalInspectionContextImpl context,
                                     Set<? super PsiElement> ignoredElements) {
    if (myFix instanceof BatchQuickFix) {
      applyFix(project, context, BatchModeDescriptorsUtil.flattenDescriptors(descriptors), ignoredElements);
    }
    else {
      super.performFixesInBatch(project, descriptors, context, ignoredElements);
    }
  }

  private void ignore(@NotNull Collection<? super PsiElement> ignoredElements,
                      @NotNull CommonProblemDescriptor descriptor,
                      boolean hasFix,
                      @NotNull GlobalInspectionContextImpl context) {
    if (hasFix) {
      InspectionToolPresentation presentation = context.getPresentation(myToolWrapper);
      presentation.resolveProblem(descriptor);
    }
    if (descriptor instanceof ProblemDescriptor problemDescriptor) {
      PsiElement element = problemDescriptor.getPsiElement();
      if (element != null) {
        ignoredElements.add(element);
      }
    }
  }
}