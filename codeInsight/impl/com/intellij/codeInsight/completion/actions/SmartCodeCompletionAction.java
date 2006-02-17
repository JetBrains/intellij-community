package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.completion.SmartCodeCompletionHandler;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

/**
 *
 */
public class SmartCodeCompletionAction extends BaseCodeInsightAction {
  public SmartCodeCompletionAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformedImpl(Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.general");
    super.actionPerformedImpl(project, editor);
  }

  protected CodeInsightActionHandler getHandler() {
    return new SmartCodeCompletionHandler();
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return file instanceof PsiJavaFile;
  }

  protected boolean isValidForLookup() {
    return true;
  }
}
