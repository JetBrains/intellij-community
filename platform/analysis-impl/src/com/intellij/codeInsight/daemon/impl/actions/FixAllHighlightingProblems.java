// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ex.ApplicationManagerEx;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.SequentialModalProgressTask;
import com.intellij.util.SequentialTask;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class FixAllHighlightingProblems implements IntentionAction {
  private final IntentionActionWithFixAllOption myAction;

  FixAllHighlightingProblems(@NotNull IntentionActionWithFixAllOption action) {
    myAction = action;
  }

  @Override
  public @NotNull String getText() {
    return myAction.getFixAllText();
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

    List<Pair<ModCommandAction, SmartPsiFileRange>> modCommands =
      ContainerUtil.map(actions, pair -> Pair.create(pair.first.asModCommandAction(), pair.second));
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    String actionName = myAction.getFamilyName();
    if (ContainerUtil.all(modCommands, pair -> pair.first != null)) {
      runModCommands(editor, file, actionName, modCommands);
    }
    else {
      ApplicationManagerEx.getApplicationEx()
        .runWriteActionWithCancellableProgressInDispatchThread(actionName, project, null, indicator -> {
          indicator.setIndeterminate(false);
          for (int i = 0; i < actions.size(); i++) {
            indicator.setFraction((double)i / actions.size());
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
  }

  private static void runModCommands(@Nullable Editor editor,
                                     @NotNull PsiFile file,
                                     @Nls String actionName,
                                     @NotNull List<Pair<ModCommandAction, SmartPsiFileRange>> modCommands) {
    Project project = file.getProject();
    Document document = file.getFileDocument();
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    final SequentialModalProgressTask progressTask =
      new SequentialModalProgressTask(project, actionName, true);
    progressTask.setMinIterationTime(200);
    ActionContext context = ActionContext.from(editor, file);
    SequentialTask task = new SequentialTask() {
      int fixNumber = 0;

      @Override
      public boolean isDone() {
        return fixNumber >= modCommands.size();
      }

      @Override
      public boolean iteration(@NotNull ProgressIndicator indicator) {
        indicator.setFraction(((double)fixNumber) / modCommands.size());
        return iteration();
      }

      @Override
      public boolean iteration() {
        Pair<ModCommandAction, SmartPsiFileRange> pair = modCommands.get(fixNumber++);
        Segment range = pair.getSecond().getRange();
        if (range != null) {
          ActionContext currentContext = context.withOffset(range.getStartOffset());
          if (pair.first.getPresentation(currentContext) != null) {
            ThrowableComputable<ModCommand, RuntimeException> computable = () -> {
              return ReadAction.nonBlocking(() -> pair.first.perform(currentContext))
                .expireWith(project)
                .executeSynchronously();
            };
            ModCommand command = ProgressManager.getInstance()
              .runProcessWithProgressSynchronously(computable, actionName, true, project);
            if (command == null) return true;
            ModCommandExecutor.getInstance().executeInBatch(currentContext, command);
            psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
            psiDocumentManager.commitDocument(document);
          }
        }
        return fixNumber == modCommands.size();
      }
    };
    progressTask.setTask(task);
    ProgressManager.getInstance().run(progressTask);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
