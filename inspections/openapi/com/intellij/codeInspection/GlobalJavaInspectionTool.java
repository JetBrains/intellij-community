/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.reference.RefManager;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

public abstract class GlobalJavaInspectionTool extends GlobalInspectionTool implements CustomSuppressableInspectionTool {
  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    return queryExternalUsagesRequests(globalContext.getRefManager(), globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT), problemDescriptionsProcessor);
  }

  protected boolean queryExternalUsagesRequests(RefManager manager, GlobalJavaInspectionContext globalContext, ProblemDescriptionsProcessor processor) {
    return false;
  }

  @Nullable
  public SuppressIntentionAction[] getSuppressActions() {
    return SuppressManager.getInstance().createSuppressActions(HighlightDisplayKey.find(getShortName()));
  }

  public boolean isSuppressedFor(final PsiElement element) {
    return SuppressManager.getInstance().isSuppressedFor(element, getShortName());
  }
}