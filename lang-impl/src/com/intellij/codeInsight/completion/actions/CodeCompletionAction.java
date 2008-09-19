package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 *  @author peter
 */
public class CodeCompletionAction extends BaseCodeInsightAction implements HintManagerImpl.ActionToIgnore{
  public CodeCompletionAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformedImpl(@NotNull Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_BASIC);
    super.actionPerformedImpl(project, editor);
  }

  public CodeInsightActionHandler getHandler() {
    return new CodeCompletionHandlerBase(CompletionType.BASIC);
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }

  protected boolean isValidForLookup() {
    return true;
  }
}
