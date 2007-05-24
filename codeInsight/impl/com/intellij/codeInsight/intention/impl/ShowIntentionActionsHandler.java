package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowIntentionsPass;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

import java.util.ArrayList;

/**
 * @author mike
 */
public class ShowIntentionActionsHandler implements CodeInsightActionHandler {
  public void invoke(Project project, Editor editor, PsiFile file) {
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    if (HintManager.getInstance().performCurrentQuestionAction()) return;

    if (!file.isWritable()) return;
    if (file instanceof PsiCodeFragment) return;

    TemplateState state = TemplateManagerImpl.getTemplateState(editor);
    if (state != null && !state.isFinished()) return;
    DaemonCodeAnalyzer.getInstance(project).autoImportReferenceAtCursor(editor, file); //let autoimport complete

    final IntentionAction[] intentionActions = IntentionManager.getInstance().getIntentionActions();

    ArrayList<HighlightInfo.IntentionActionDescriptor> intentionsToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    ArrayList<HighlightInfo.IntentionActionDescriptor> errorFixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    ArrayList<HighlightInfo.IntentionActionDescriptor> inspectionFixesToShow = new ArrayList<HighlightInfo.IntentionActionDescriptor>();

    ShowIntentionsPass.getActionsToShow(editor, file, intentionsToShow, errorFixesToShow, inspectionFixesToShow, intentionActions, -1);

    if (!intentionsToShow.isEmpty() || !errorFixesToShow.isEmpty() || !inspectionFixesToShow.isEmpty()) {
      IntentionHintComponent.showIntentionHint(project, file, editor, intentionsToShow, errorFixesToShow, inspectionFixesToShow, true);
    }
    else {
//      Toolkit.getDefaultToolkit().beep();
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
