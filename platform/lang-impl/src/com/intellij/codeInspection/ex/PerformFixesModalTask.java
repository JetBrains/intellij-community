// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.CommonProblemDescriptor;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.QuickFix;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.util.SequentialTask;
import one.util.streamex.EntryStream;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.intellij.modcommand.ModCommandExecutor.BatchExecutionResult;
import static com.intellij.modcommand.ModCommandExecutor.Result;
import static com.intellij.openapi.util.text.StringUtil.notNullize;

public abstract class PerformFixesModalTask implements SequentialTask {
  protected final @NotNull Project myProject;
  private final List<CommonProblemDescriptor[]> myDescriptorPacks;
  private final PsiDocumentManager myDocumentManager;
  private final PostprocessReformattingAspect myReformattingAspect;
  private final int myLength;
  protected final @NotNull Map<@NotNull BatchExecutionResult, Integer> myResultCount = new HashMap<>();

  private int myProcessed;
  private int myPackIdx;
  private int myDescriptorIdx;

  protected PerformFixesModalTask(@NotNull Project project,
                                  CommonProblemDescriptor @NotNull [] descriptors) {
    this(project, Collections.singletonList(descriptors));
  }

  protected PerformFixesModalTask(@NotNull Project project,
                                  @NotNull List<CommonProblemDescriptor[]> descriptorPacks) {
    myProject = project;
    myDescriptorPacks = descriptorPacks;
    myLength = descriptorPacks.stream().mapToInt(ds -> ds.length).sum();
    myDocumentManager = PsiDocumentManager.getInstance(myProject);
    myReformattingAspect = PostprocessReformattingAspect.getInstance(myProject);
  }

  @Override
  public boolean isDone() {
    return myPackIdx > myDescriptorPacks.size() - 1;
  }

  @Override
  public boolean iteration() {
    return true;
  }

  public int getNumberOfSucceededFixes() {
    return myResultCount.get(Result.SUCCESS);
  }

  public void doRun(ProgressIndicator indicator) {
    indicator.setIndeterminate(false);
    while (!isDone()) {
      if (indicator.isCanceled()) {
        break;
      }
      iteration(indicator);
    }
  }

  @Override
  public boolean iteration(@NotNull ProgressIndicator indicator) {
    final @Nullable Pair<CommonProblemDescriptor, Boolean> pair = nextDescriptor();
    if (pair == null) {
      return isDone();
    }

    CommonProblemDescriptor descriptor = pair.getFirst();
    boolean shouldDoPostponedOperations = pair.getSecond();

    beforeProcessing(descriptor);

    indicator.setFraction((double)myProcessed++ / myLength);
    String presentableText = notNullize(getPresentableText(descriptor), "usages");
    indicator.setText(InspectionsBundle.message("processing.progress.text", presentableText));

    boolean runInReadAction = mustRunInReadAction(descriptor);

    ApplicationManager.getApplication().runWriteAction(() -> {
      myDocumentManager.commitAllDocuments();
      if (!runInReadAction) {
        applyFix(myProject, descriptor);
        if (shouldDoPostponedOperations) {
          myReformattingAspect.doPostponedFormatting();
        }
      }
    });
    if (runInReadAction) {
      applyFix(myProject, descriptor);
    }
    return isDone();
  }

  private static boolean mustRunInReadAction(@NotNull CommonProblemDescriptor descriptor) {
    boolean runInReadAction = false;
    QuickFix<?>[] fixes = descriptor.getFixes();
    if (fixes != null) {
      for (QuickFix<?> fix : fixes) {
        if (!fix.startInWriteAction()) {
          runInReadAction = true;
        }
        else {
          runInReadAction = false;
          break;
        }
      }
    }
    return runInReadAction;
  }

  protected abstract void applyFix(Project project, CommonProblemDescriptor descriptor);

  protected void beforeProcessing(@NotNull CommonProblemDescriptor descriptor) {
  }

  protected @Nullable @NlsSafe String getPresentableText(@NotNull CommonProblemDescriptor descriptor) {
    if (!(descriptor instanceof ProblemDescriptor)) return null;

    PsiElement psiElement = ((ProblemDescriptor)descriptor).getPsiElement();
    return psiElement != null ? notNullize(SymbolPresentationUtil.getSymbolPresentableText(psiElement)) : null;
  }

  /**
   * @param actionName user-readable name of the action
   * @return error message text; null if the action was successful
   */
  public final @Nullable @NlsContexts.Tooltip String getResultMessage(@NotNull String actionName) {
    if (myResultCount.isEmpty()) {
      return LangBundle.message("hint.text.unfortunately.currently.available.for.batch.mode", actionName);
    }
    if (myResultCount.size() == 1) {
      BatchExecutionResult result = myResultCount.keySet().iterator().next();
      if (result == Result.SUCCESS) return null;
      return result.getMessage();
    }
    @Nls String message = LangBundle.message("executor.error.some.actions.failed") + "\n";
    int total = myResultCount.values().stream().mapToInt(i -> i).sum();
    return message + EntryStream.of(myResultCount) //NON-NLS
      .reverseSorted(Map.Entry.comparingByValue())
      .mapKeyValue((result, count) -> LangBundle.message("executor.one.of.actions", count, total, result.getMessage()))
      .joining("\n");
  }

  private @Nullable Pair<CommonProblemDescriptor, Boolean> nextDescriptor() {
    CommonProblemDescriptor[] descriptors = myDescriptorPacks.get(myPackIdx);

    boolean shouldDoPostponedOperations = myDescriptorIdx == descriptors.length - 1;
    Pair<CommonProblemDescriptor, Boolean> result =
      myDescriptorIdx >= descriptors.length
        ? null
        : Pair.create(descriptors[myDescriptorIdx++], shouldDoPostponedOperations);

    if (myDescriptorIdx == descriptors.length) {
      myPackIdx++;
      myDescriptorIdx = 0;
    }

    return result;
  }
}