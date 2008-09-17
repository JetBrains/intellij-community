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

/**
 *
 */
public class ClassNameCompletionAction extends BaseCodeInsightAction implements HintManagerImpl.ActionToIgnore{
  public ClassNameCompletionAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformedImpl(Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_CLASSNAME);
    super.actionPerformedImpl(project, editor);
  }

  protected CodeInsightActionHandler getHandler() {
    return new CodeCompletionHandlerBase(CompletionType.CLASS_NAME);
  }

  protected boolean isValidForLookup() {
    return true;
  }
}
