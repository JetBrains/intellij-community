// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.ExecutionException;

public class CleanupInspectionIntention implements IntentionAction, HighPriorityAction {
  @NotNull
  private final InspectionToolWrapper<?,?> myToolWrapper;
  private final FileModifier myQuickfix;
  @Nullable private final PsiFile myFile;
  private final String myText;

  public CleanupInspectionIntention(@NotNull InspectionToolWrapper<?,?> toolWrapper,
                                    @NotNull FileModifier quickFix,
                                    @Nullable PsiFile file,
                                    String text) {
    myToolWrapper = toolWrapper;
    myQuickfix = quickFix;
    myFile = file;
    myText = text;
  }

  @Override
  @NotNull
  public String getText() {
    return InspectionsBundle.message("fix.all.inspection.problems.in.file", myToolWrapper.getDisplayName());
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed() : "do not run under write action";
    PsiFile targetFile = myFile == null ? file : myFile;
    List<ProblemDescriptor> descriptions;
    try {
      descriptions = ReadAction.nonBlocking(() -> ProgressManager.getInstance().runProcess(() -> {
              InspectionManager inspectionManager = InspectionManager.getInstance(project);
              return InspectionEngine.runInspectionOnFile(targetFile, myToolWrapper, inspectionManager.createNewGlobalContext());
            }, new DaemonProgressIndicator())).submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }

    if (descriptions.isEmpty() || !FileModificationService.getInstance().preparePsiElementForWrite(targetFile)) return;

    final AbstractPerformFixesTask fixesTask = CleanupInspectionUtil.getInstance().applyFixes(project, LangBundle.message("apply.fixes"), descriptions, myQuickfix.getClass(), myQuickfix.startInWriteAction());

    if (!fixesTask.isApplicableFixFound()) {
      HintManager.getInstance().showErrorHint(editor,
                                              LangBundle.message("hint.text.unfortunately.currently.available.for.batch.mode", myText));
    }
  }

  @Override
  public boolean isAvailable(@NotNull final Project project, final Editor editor, final PsiFile file) {
    return myQuickfix.getClass() != EmptyIntentionAction.class &&
           (myQuickfix.startInWriteAction() || myQuickfix instanceof BatchQuickFix) &&
           editor != null &&
           !(myToolWrapper instanceof LocalInspectionToolWrapper && ((LocalInspectionToolWrapper)myToolWrapper).isUnfair());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
