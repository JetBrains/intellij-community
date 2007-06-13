package com.intellij.codeInsight.completion.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.completion.ClassNameCompletionHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;

/**
 *
 */
public class ClassNameCompletionAction extends BaseCodeInsightAction {
  public ClassNameCompletionAction() {
    setEnabledInModalContext(true);
  }

  public void actionPerformedImpl(Project project, Editor editor) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed("editing.completion.classname");
    super.actionPerformedImpl(project, editor);
  }

  protected CodeInsightActionHandler getHandler() {
    return new ClassNameCompletionHandler();
  }

  public boolean isValidForLookup(Lookup lookup, DataContext context) {
    if (lookup == null) return false;
    final LookupItem item = lookup.getCurrentItem();
    if (item == null) return false;
    
    final Object o = item.getObject();
    return o instanceof PsiElement && isValidForFile(null, null, ((PsiElement)o).getContainingFile());    
  }

  protected boolean isValidForLookup() {
    return true;
  }
}
