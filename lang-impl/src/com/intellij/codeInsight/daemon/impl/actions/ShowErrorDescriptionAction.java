package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.ShowErrorDescriptionHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiFile;

public class ShowErrorDescriptionAction extends BaseCodeInsightAction{
  private static int width;
  private static boolean shouldShowDescription = false;
  private static boolean descriptionShown = true;

  public ShowErrorDescriptionAction() {
    setEnabledInModalContext(true);
  }

  protected CodeInsightActionHandler getHandler() {
    return new ShowErrorDescriptionHandler(shouldShowDescription ? width : 0);
  }

  protected boolean isValidForFile(Project project, Editor editor, PsiFile file) {
    return DaemonCodeAnalyzer.getInstance(project).isHighlightingAvailable(file);
  }

  protected boolean isEnabledForFile(Project project, Editor editor, PsiFile file) {
    DaemonCodeAnalyzer codeAnalyzer = DaemonCodeAnalyzer.getInstance(project);
    HighlightInfo info =
      ((DaemonCodeAnalyzerImpl)codeAnalyzer).findHighlightByOffset(editor.getDocument(), editor.getCaretModel().getOffset(), false);
    return info != null && info.description != null;
  }

  public void beforeActionPerformedUpdate(final AnActionEvent e) {
    super.beforeActionPerformedUpdate(e);
    changeState();
  }

  private static void changeState() {
    if (Comparing.strEqual(ActionManagerEx.getInstanceEx().getPrevPreformedActionId(), IdeActions.ACTION_SHOW_ERROR_DESCRIPTION)) {
      shouldShowDescription = descriptionShown;
    } else {
      shouldShowDescription = false;
      descriptionShown = true;
    }
  }

  public static void rememberCurrentWidth(int currentWidth) {
    width = currentWidth;
    descriptionShown = !shouldShowDescription;
  }

}
