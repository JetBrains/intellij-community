package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.completion.SmartCodeCompletionHandler;
import com.intellij.codeInsight.completion.CodeCompletionHandler;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;

/**
 *
 */
public class SmartCodeCompletionAction extends BaseCodeInsightAction {
  private static boolean ourDoingSmartCodeCompleteAction;

  public SmartCodeCompletionAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformedImpl(Project project, Editor editor) {
    super.actionPerformedImpl(project, editor);
  }

  protected CodeInsightActionHandler getHandler() {
    return createHandler();
  }

  public static CodeInsightActionHandler createHandler() {
    return new CodeInsightActionHandler() {
      public void invoke(Project project, Editor editor, PsiFile file) {
        try {
          ourDoingSmartCodeCompleteAction = true;
          if (file instanceof PsiJavaFile) {
            FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.smarttype.general");
            new SmartCodeCompletionHandler().invoke(project, editor, file);
          } else {
            FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.basic");
            new CodeCompletionHandler().invoke(project, editor, file);
          }
        }
        finally {
          ourDoingSmartCodeCompleteAction = false;
        }
      }

      public boolean startInWriteAction() {
        return true;
      }
    };
  }

  protected boolean isValidForFile(Project project, Editor editor, final PsiFile file) {
    return true;
  }

  protected boolean isValidForLookup() {
    return true;
  }

  public static boolean isDoingSmartCodeCompleteAction() {
    return ourDoingSmartCodeCompleteAction;
  }
}
