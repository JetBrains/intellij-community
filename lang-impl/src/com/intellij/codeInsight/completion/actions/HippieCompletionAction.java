package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.featureStatistics.FeatureUsageTracker;
import org.jetbrains.annotations.NotNull;

public class HippieCompletionAction extends BaseCodeInsightAction {
  public HippieCompletionAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformedImpl(@NotNull Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.hippie");
    super.actionPerformedImpl(project, editor);
  }

  protected CodeInsightActionHandler getHandler() {
    return new HippieWordCompletionHandler(HippieWordCompletionHandler.Direction.FORWARD);
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }
}
