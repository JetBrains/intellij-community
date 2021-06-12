// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
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
    List<Pair<IntentionAction, SmartPsiFileRange>> actions = new ArrayList<>();
    Document document = editor.getDocument();
    Processor<HighlightInfo> processor = info -> {
      IntentionAction fix = info.getSameFamilyFix(myAction);
      if (fix != null) {
        TextRange range = TextRange.create(info.getActualStartOffset(), info.getActualEndOffset());
        SmartPsiFileRange pointer = SmartPointerManager.getInstance(project)
          .createSmartPsiFileRangePointer(file, range);
        actions.add(Pair.create(fix, pointer));
      }
      return true;
    };
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ReadAction.run(() -> {
        DaemonCodeAnalyzerEx.processHighlights(
          document, project, null, 0, document.getTextLength(), processor);
      });
    }, AnalysisBundle.message("command.name.gather.fixes"), true, project)) return;

    if (actions.isEmpty() || !FileModificationService.getInstance().preparePsiElementForWrite(file)) return;
    // Applying in reverse order looks safer
    Collections.reverse(actions);

    ApplicationManagerEx.getApplicationEx()
      .runWriteActionWithCancellableProgressInDispatchThread(myAction.getFamilyName(), project, null, indicator -> {
        indicator.setIndeterminate(false);
        PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
        for (int i = 0; i < actions.size(); i++) {
          indicator.setFraction((double) i / actions.size());
          Pair<IntentionAction, SmartPsiFileRange> pair = actions.get(i);
          IntentionAction action = pair.getFirst();
          // Some actions rely on the caret position
          Segment range = pair.getSecond().getRange();
          if (range != null) {
            editor.getCaretModel().moveToOffset(range.getStartOffset());
            if (action.isAvailable(project, editor, file)) {
              action.invoke(project, editor, file);
              psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
              psiDocumentManager.commitDocument(document);
            }
          }
        }
      });
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
