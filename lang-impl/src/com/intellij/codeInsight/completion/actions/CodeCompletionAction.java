package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.completion.CodeCompletionFeatures;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 *  @author peter
 */
public class CodeCompletionAction extends BaseCodeCompletionAction {

  public void actionPerformedImpl(@NotNull Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(CodeCompletionFeatures.EDITING_COMPLETION_BASIC);
    super.actionPerformedImpl(project, editor);
  }

  public CodeCompletionHandlerBase getHandler() {
    return new CodeCompletionHandlerBase(CompletionType.BASIC);
  }

}
