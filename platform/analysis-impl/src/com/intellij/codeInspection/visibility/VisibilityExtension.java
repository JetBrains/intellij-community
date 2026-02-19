// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.visibility;

import com.intellij.codeInspection.GlobalInspectionContext;
import com.intellij.codeInspection.GlobalInspectionTool;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptionsProcessor;
import com.intellij.codeInspection.reference.RefManager;
import org.jetbrains.annotations.NotNull;

/**
 * Allows reducing number of warnings reported by 
 * {@link com.intellij.codeInspection.visibility.VisibilityInspection}
 */
public interface VisibilityExtension {
  /**
   * Explicitly ignore elements those visibilities should not be changed. 
   * Called during {@link GlobalInspectionTool#queryExternalUsagesRequests(InspectionManager, GlobalInspectionContext, ProblemDescriptionsProcessor)}
   * when all candidates are already collected.
   * <p/>
   * @see ProblemDescriptionsProcessor#ignoreElement(com.intellij.codeInspection.reference.RefEntity) 
   */
  void fillIgnoreList(@NotNull RefManager refManager, @NotNull ProblemDescriptionsProcessor processor);
}
