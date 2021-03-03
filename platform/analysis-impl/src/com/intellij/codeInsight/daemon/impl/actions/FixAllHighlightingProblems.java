// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class FixAllHighlightingProblems implements IntentionAction {
  private final IntentionActionWithFixAllOption myAction;

  public FixAllHighlightingProblems(IntentionActionWithFixAllOption action) {
    myAction = action;
  }

  @Override
  public @NotNull String getText() {
    return AnalysisBundle.message("intention.name.apply.all.fixes.in.file", myAction.getFamilyName());
  }

  @Override
  public @NotNull String getFamilyName() {
    return AnalysisBundle.message("intention.family.name.fix.all.problems.like.this");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             Editor editor,
                             PsiFile file) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    // IntentionAction, offset
    List<Pair<IntentionAction, Integer>> actions = new ArrayList<>();
    Document document = editor.getDocument();
    ProgressManager.getInstance().runProcess(() -> {
      DaemonCodeAnalyzerEx.processHighlights(document, project, HighlightSeverity.ERROR, 0, document.getTextLength(),
                                             info -> {
                                               ProgressManager.checkCanceled();
                                               IntentionAction fix = info.getSameFamilyFix(myAction);
                                               if (fix != null) {
                                                 actions.add(Pair.create(fix, info.getActualStartOffset()));
                                               }
                                               return true;
                                             });
    }, new DaemonProgressIndicator());

    if (actions.isEmpty() || !FileModificationService.getInstance().preparePsiElementForWrite(file)) return;

    String message = AnalysisBundle.message("command.name.apply.fixes");
    CommandProcessor.getInstance().executeCommand(project, () -> {
      ApplicationManagerEx.getApplicationEx()
        .runWriteActionWithCancellableProgressInDispatchThread(message, project, null, indicator -> {
          PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
          for (Pair<IntentionAction, Integer> pair : actions) {
            IntentionAction action = pair.getFirst();
            // Some actions rely on the caret position
            editor.getCaretModel().moveToOffset(pair.getSecond());
            if (action.isAvailable(project, editor, file)) {
              action.invoke(project, editor, file);
              psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
              psiDocumentManager.commitDocument(document);
            }
          }
        });
    }, message, null);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
