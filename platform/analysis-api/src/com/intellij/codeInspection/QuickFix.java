// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Common base interface for quick fixes provided by local and global inspections.
 *
 * @author anna
 * @see CommonProblemDescriptor#getFixes()
 */
public interface QuickFix<D extends CommonProblemDescriptor> extends WriteActionAware {
  QuickFix[] EMPTY_ARRAY = new QuickFix[0];

  /**
   * @return the name of the quick fix.
   */
  default @IntentionName @NotNull String getName() {
    return getFamilyName();
  }

  /**
   * @return text to appear in "Apply Fix" popup when multiple Quick Fixes exist (in the results of batch code inspection). For example,
   * if the name of the quickfix is "Create template &lt;filename&gt", the return value of getFamilyName() should be "Create template".
   * If the name of the quickfix does not depend on a specific element, simply return {@link #getName()}.
   */
  @IntentionFamilyName
  @NotNull
  String getFamilyName();

  /**
   * Called to apply the fix.
   * <p>
   * Please call {@link com.intellij.profile.codeInspection.ProjectInspectionProfileManager#fireProfileChanged()} if inspection profile is changed as result of fix.
   * This function is called under write action if {@link #startInWriteAction()} is not disabled.
   *
   * @param project    {@link Project}
   * @param descriptor problem reported by the tool which provided this quick fix action
   * @see #startInWriteAction()
   */
  void applyFix(@NotNull Project project, @NotNull D descriptor);
}
