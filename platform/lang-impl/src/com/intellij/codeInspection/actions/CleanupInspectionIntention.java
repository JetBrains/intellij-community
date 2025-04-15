// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@ApiStatus.Internal
public final class CleanupInspectionIntention implements IntentionAction, HighPriorityAction {
  private final @NotNull InspectionToolWrapper<?,?> myToolWrapper;
  private final FileModifier myQuickfix;
  private final @Nullable PsiFile myFile;
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
  public @NotNull String getText() {
    return InspectionsBundle.message("fix.all.inspection.problems.in.file", myToolWrapper.getDisplayName());
  }

  @Override
  public @NotNull String getFamilyName() {
    return getText();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    String message = findAndFix(project, file);

    if (message != null) {
      HintManager.getInstance().showErrorHint(editor, message);
    }
  }

  public @NlsContexts.HintText @Nullable String findAndFix(@NotNull Project project, PsiFile file) {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed() : "do not run under write action";
    PsiFile targetFile = myFile == null ? file : myFile;
    if (targetFile == null) return null;
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(targetFile.getProject());
    boolean injected = manager.isInjectedFragment(targetFile);
    List<ProblemDescriptor> descriptions;
    if (injected) {
      descriptions = new ArrayList<>();
      PsiFile topLevelFile = manager.getTopLevelFile(targetFile);
      PsiTreeUtil.processElements(topLevelFile, PsiLanguageInjectionHost.class, host ->  {
        manager.enumerateEx(host, topLevelFile, false, (injectedPsi, places) -> {
          descriptions.addAll(getDescriptors(project, injectedPsi));
        });
        return true;
      });
    }
    else {
      descriptions = getDescriptors(project, targetFile);
    }

    String message = null;
    if (!descriptions.isEmpty() && FileModificationService.getInstance().preparePsiElementForWrite(targetFile)) {
      AbstractPerformFixesTask fixesTask = CleanupInspectionUtil.getInstance()
        .applyFixes(project, LangBundle.message("apply.fixes"), descriptions, ReportingClassSubstitutor.getClassToReport(myQuickfix), 
                    myQuickfix.startInWriteAction());

      message = fixesTask.getResultMessage(myText);
    }
    return message;
  }

  private List<ProblemDescriptor> getDescriptors(@NotNull Project project, PsiFile targetFile) {
    try {
      return ReadAction.nonBlocking(() -> ProgressManager.getInstance().runProcess(() -> {
        InspectionManager inspectionManager = InspectionManager.getInstance(project);
        return InspectionEngine.runInspectionOnFile(targetFile, myToolWrapper, inspectionManager.createNewGlobalContext());
      }, new DaemonProgressIndicator())).submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myQuickfix.getClass() != EmptyIntentionAction.class &&
           (myQuickfix.startInWriteAction() || myQuickfix instanceof BatchQuickFix || myQuickfix instanceof ModCommandQuickFix) &&
           editor != null && !(myToolWrapper instanceof LocalInspectionToolWrapper wrapper && wrapper.isUnfair());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
