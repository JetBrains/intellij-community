// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.multiverse.CodeInsightContext;
import com.intellij.codeInsight.multiverse.EditorContextManager;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModCommandService;
import com.intellij.modcommand.ModCommandWithContext;
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
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageEditorUtil;
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
                             PsiFile psiFile) {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(project);
    PsiFile topLevelFile = manager.getTopLevelFile(psiFile);
    Editor topLevelEditor = InjectedLanguageEditorUtil.getTopLevelEditor(editor);
    // IntentionAction, offset
    List<Pair<IntentionAction, SmartPsiFileRange>> actions = new ArrayList<>();
    Document document = topLevelFile.getFileDocument();
    Processor<HighlightInfo> processor = info -> {
      IntentionAction fix = info.getSameFamilyFix(myAction);
      if (fix != null) {
        TextRange range = TextRange.create(info.getActualStartOffset(), info.getActualEndOffset());
        SmartPsiFileRange pointer = SmartPointerManager.getInstance(project)
          .createSmartPsiFileRangePointer(topLevelFile, range);
        actions.add(Pair.create(fix, pointer));
      }
      return true;
    };
    CodeInsightContext context = EditorContextManager.getEditorContext(editor, project);
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
      ReadAction.run(() -> {
        DaemonCodeAnalyzerEx.processHighlights(
          document, project, null, 0, document.getTextLength(), context, processor);
      });
    }, AnalysisBundle.message("command.name.gather.fixes"), true, project)) return;

    if (actions.isEmpty() || !FileModificationService.getInstance().preparePsiElementForWrite(psiFile)) return;
    // Applying in reverse order looks safer
    Collections.reverse(actions);

    List<Pair<ModCommandAction, SmartPsiFileRange>> modCommands =
      ContainerUtil.map(actions, pair -> Pair.create(pair.first.asModCommandAction(), pair.second));
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    String actionName = myAction.getFamilyName();
    if (ContainerUtil.all(modCommands, pair -> pair.first != null)) {
      runModCommands(topLevelEditor, topLevelFile, actionName, modCommands);
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
              topLevelEditor.getCaretModel().moveToOffset(range.getStartOffset());
              if (action.isAvailable(project, editor, psiFile)) {
                action.invoke(project, editor, psiFile);
                psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
                psiDocumentManager.commitDocument(document);
              }
            }
          }
        });
    }
  }

  private static void runModCommands(@Nullable Editor editor,
                                     @NotNull PsiFile psiFile,
                                     @Nls String actionName,
                                     @NotNull List<Pair<ModCommandAction, SmartPsiFileRange>> modCommands) {
    Project project = psiFile.getProject();
    Document document = psiFile.getFileDocument();
    PsiDocumentManager psiDocumentManager = PsiDocumentManager.getInstance(project);
    final SequentialModalProgressTask progressTask =
      new SequentialModalProgressTask(project, actionName, true);
    progressTask.setMinIterationTime(200);
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
          ModCommandWithContext contextAndCommand =
            ModCommandService.getInstance().chooseFileAndPerform(psiFile, editor, pair.first, range.getStartOffset());
          if (contextAndCommand == null) return true;
          contextAndCommand.executeInBatch();
          psiDocumentManager.doPostponedOperationsAndUnblockDocument(document);
          psiDocumentManager.commitDocument(document);
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
