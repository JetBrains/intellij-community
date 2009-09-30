package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.LocalInspectionsPass;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.idea.ActionsBundle;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author mike
 */
public class ShowIntentionActionsHandler implements CodeInsightActionHandler {
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (HintManagerImpl.getInstanceImpl().performCurrentQuestionAction()) return;

    //intentions check isWritable before modification: if (!file.isWritable()) return;
    if (file instanceof PsiCodeFragment) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state != null && !state.isFinished()) return;

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    codeAnalyzer.autoImportReferenceAtCursor(editor, file); //let autoimport complete

    ShowIntentionsPass.IntentionsInfo intentions = new ShowIntentionsPass.IntentionsInfo();
    ShowIntentionsPass.getActionsToShow(editor, file, intentions, -1);
    
    if (!codeAnalyzer.isAllAnalysisFinished(file)) {
      runPassesAndShowIntentions(project, editor, file, intentions);
    }
    else if (!intentions.isEmpty()) {
      IntentionHintComponent.showIntentionHint(project, file, editor, intentions, true);
    }
  }

  private static void runPassesAndShowIntentions(final Project project, final Editor editor, final PsiFile file, final ShowIntentionsPass.IntentionsInfo intentions) {
    intentions.errorFixesToShow.clear();
    intentions.inspectionFixesToShow.clear();
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return;
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    final int offset = editor.getCaretModel().getOffset();
    final PsiElement element = file.findElementAt(offset);
    if (element == null) return;
    final Task.Backgroundable task = new Task.Backgroundable(project, ActionsBundle.message("action.ShowIntentionActions.text"), true) {
      public void run(@NotNull final ProgressIndicator indicator) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
            final TextRange textRange = element.getTextRange();
            final LocalInspectionsPass pass = new LocalInspectionsPass(file, document, textRange.getStartOffset(), textRange.getEndOffset());
            pass.collectInformation(indicator);
            for (HighlightInfo info : pass.getHighlights()) {
              if (info.quickFixActionRanges != null) {
                final boolean isError = info.getSeverity() == HighlightSeverity.ERROR;
                for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> actionRanges : info.quickFixActionRanges) {
                  if (actionRanges.second.contains(offset)) {
                    final IntentionAction action = actionRanges.first.getAction();
                    boolean available = action instanceof PsiElementBaseIntentionAction ?
                                        ((PsiElementBaseIntentionAction)action).isAvailable(project, editor, element) :
                                        action.isAvailable(project, editor, file);
                    if (available) {
                      if (isError) {
                        intentions.errorFixesToShow.add(actionRanges.first);
                      }
                      else {
                        intentions.inspectionFixesToShow.add(actionRanges.first);
                      }
                    }
                  }
                }
              }
            }

          }
        });
        SwingUtilities.invokeLater(new Runnable(){
          public void run() {
            if (editor.getComponent().isDisplayable()) {
              if (!intentions.isEmpty()) {
                IntentionHintComponent.showIntentionHint(project, file, editor, intentions, true);
              }
            }
          }
        });
      }
    };
    ProgressManager.getInstance().run(task);
  }

  public boolean startInWriteAction() {
    return false;
  }
}
