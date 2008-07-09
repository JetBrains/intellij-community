package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.LocalInspectionsPass;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
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
import java.util.ArrayList;

/**
 * @author mike
 */
public class ShowIntentionActionsHandler implements CodeInsightActionHandler {
  public void invoke(final Project project, final Editor editor, final PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (HintManager.getInstance().performCurrentQuestionAction()) return;

    if (!file.isWritable()) return;
    if (file instanceof PsiCodeFragment) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state != null && !state.isFinished()) return;

    final DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    codeAnalyzer.autoImportReferenceAtCursor(editor, file); //let autoimport complete

    final IntentionAction[] intentionActions = IntentionManager.getInstance().getIntentionActions();

    final ArrayList<HighlightInfo.IntentionActionDescriptor> intentionsToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    final ArrayList<HighlightInfo.IntentionActionDescriptor> errorFixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    final ArrayList<HighlightInfo.IntentionActionDescriptor> inspectionFixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();

    ShowIntentionsPass.getActionsToShow(editor, file, intentionsToShow, errorFixesToShow, inspectionFixesToShow, intentionActions, -1);
    
    if (!codeAnalyzer.isAllAnalysisFinished(file)) {
      errorFixesToShow.clear();
      inspectionFixesToShow.clear();
      final VirtualFile virtualFile = file.getVirtualFile();
      if (virtualFile == null) return;
      final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
      final int offset = editor.getCaretModel().getOffset();
      final PsiElement element = file.findElementAt(offset);
      final Task.Backgroundable task = new Task.Backgroundable(project, ActionsBundle.message("action.ShowIntentionActions.text"), true) {
        public void run(@NotNull final ProgressIndicator indicator) {
          ApplicationManager.getApplication().runReadAction(new Runnable() {
            public void run() {
              final TextRange textRange = element.getTextRange();
              final LocalInspectionsPass pass =
                  new LocalInspectionsPass(file, document, textRange.getStartOffset(), textRange.getEndOffset());
              pass.collectInformation(indicator);
              for (HighlightInfo info : pass.getHighlights()) {
                if (info.quickFixActionRanges != null) {
                  final boolean isError = info.getSeverity() == HighlightSeverity.ERROR;
                  for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> actionRanges : info.quickFixActionRanges) {
                    if (actionRanges.second.contains(offset) && actionRanges.first.getAction().isAvailable(project, editor, file)) {
                      if (isError) {
                        errorFixesToShow.add(actionRanges.first);
                      }
                      else {
                        inspectionFixesToShow.add(actionRanges.first);
                      }
                    }
                  }
                }
              }

            }
          });
          SwingUtilities.invokeLater(new Runnable(){
            public void run() {
              if (!intentionsToShow.isEmpty() || !errorFixesToShow.isEmpty() || !inspectionFixesToShow.isEmpty()) {
                IntentionHintComponent
                    .showIntentionHint(project, file, editor, intentionsToShow, errorFixesToShow, inspectionFixesToShow, true);
              }
            }
          });
        }
      };
      ProgressManager.getInstance().run(task);
    } else {

      if (!intentionsToShow.isEmpty() || !errorFixesToShow.isEmpty() || !inspectionFixesToShow.isEmpty()) {
        IntentionHintComponent.showIntentionHint(project, file, editor, intentionsToShow, errorFixesToShow, inspectionFixesToShow, true);
      }
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
