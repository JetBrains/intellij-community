// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.actions;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.BatchQuickFix;
import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.InspectionEngine;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ex.GlobalInspectionContextBase;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolWrapper;
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.lang.LangBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.ReportingClassSubstitutor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiLanguageInjectionHost;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.PairProcessor;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@ApiStatus.Internal
public final class CleanupInspectionIntention implements IntentionAction, HighPriorityAction {
  private final @NotNull InspectionToolWrapper<?,?> myToolWrapper;
  private final FileModifier myQuickfix;
  private final @Nullable PsiFile myPsiFile;
  private final String myText;

  public CleanupInspectionIntention(@NotNull InspectionToolWrapper<?,?> toolWrapper,
                                    @NotNull FileModifier quickFix,
                                    @Nullable PsiFile psiFile,
                                    String text) {
    myToolWrapper = toolWrapper;
    myQuickfix = quickFix;
    myPsiFile = psiFile;
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
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    String message = findAndFix(project, psiFile);

    if (message != null) {
      HintManager.getInstance().showErrorHint(editor, message);
    }
  }

  public @NlsContexts.HintText @Nullable String findAndFix(@NotNull Project project, PsiFile psiFile) {
    assert !ApplicationManager.getApplication().isWriteAccessAllowed() : "do not run under write action";
    PsiFile targetFile = myPsiFile == null ? psiFile : myPsiFile;
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
        GlobalInspectionContext context = inspectionManager.createNewGlobalContext();
        String id = myToolWrapper.getMainToolId();
        List<LocalInspectionToolWrapper> wrappers;
        if (id != null && myToolWrapper instanceof LocalInspectionToolWrapper local) {
          InspectionProfileImpl profile = ((GlobalInspectionContextBase)context).getCurrentProfile();
          LocalInspectionToolWrapper mainTool = (LocalInspectionToolWrapper)profile.getToolById(id, targetFile);
          wrappers = mainTool != null ? List.of(local, mainTool) : List.of(local);
          List<ProblemDescriptor> found = Collections.synchronizedList(new ArrayList<>());
          Map<LocalInspectionToolWrapper, List<ProblemDescriptor>> map =
            InspectionEngine.inspectEx(wrappers, targetFile, targetFile.getTextRange(), targetFile.getTextRange(),
                                       false, false, true, new EmptyProgressIndicator(), PairProcessor.alwaysTrue());
          for (List<ProblemDescriptor> value : map.values()) {
            found.addAll(value);
          }
          return found;
        }
        else {
          return InspectionEngine.runInspectionOnFile(targetFile, myToolWrapper, context);
        }
      }, new DaemonProgressIndicator())).submit(AppExecutorUtil.getAppExecutorService()).get();
    }
    catch (InterruptedException | ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    return myQuickfix.getClass() != EmptyIntentionAction.class &&
           (myQuickfix.startInWriteAction() || myQuickfix instanceof BatchQuickFix || myQuickfix instanceof ModCommandQuickFix) &&
           editor != null && !(myToolWrapper instanceof LocalInspectionToolWrapper wrapper && wrapper.isUnfair());
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
