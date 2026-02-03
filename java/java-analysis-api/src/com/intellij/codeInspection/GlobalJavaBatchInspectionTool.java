// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection;

import com.intellij.codeInspection.reference.RefManager;
import org.jetbrains.annotations.NotNull;

public abstract class GlobalJavaBatchInspectionTool extends GlobalInspectionTool {
  @Override
  public boolean queryExternalUsagesRequests(final @NotNull InspectionManager manager,
                                             final @NotNull GlobalInspectionContext globalContext,
                                             final @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    return queryExternalUsagesRequests(globalContext.getRefManager(), globalContext.getExtension(GlobalJavaInspectionContext.CONTEXT), problemDescriptionsProcessor);
  }

  @Override
  public boolean isReadActionNeeded() {
    return false;
  }

  protected boolean queryExternalUsagesRequests(@NotNull RefManager manager, @NotNull GlobalJavaInspectionContext globalContext, @NotNull ProblemDescriptionsProcessor processor) {
    return false;
  }

}
