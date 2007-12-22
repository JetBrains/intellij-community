/*
 * User: anna
 * Date: 19-Dec-2007
 */
package com.intellij.codeInspection;

import com.intellij.codeInspection.reference.RefManager;

public abstract class GlobalJavaInspectionTool extends GlobalInspectionTool {
  public boolean queryExternalUsagesRequests(final InspectionManager manager,
                                             final GlobalInspectionContext globalContext,
                                             final ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    return queryExternalUsagesRequests(globalContext.getRefManager(), globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT), problemDescriptionsProcessor);
  }

  protected boolean queryExternalUsagesRequests(RefManager manager, GlobalJavaInspectionContext globalContext, ProblemDescriptionsProcessor processor) {
    return false;
  }
}