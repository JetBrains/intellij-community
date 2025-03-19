// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;

/**
 * @deprecated Use {@link JavaFileCodeStyleFacade} for per-file code-style-settings. Note: project settings
 * may not be applicable to a particular file.
 */
@ApiStatus.ScheduledForRemoval
@Deprecated
public abstract class JavaCodeStyleSettingsFacade {

  /**
   * @deprecated Use {@link JavaFileCodeStyleFacade#useFQClassNames()}
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract boolean useFQClassNames();

  /**
   * @deprecated Use {@link JavaFileCodeStyleFacade#isGenerateFinalParameters()}
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public abstract boolean isGenerateFinalParameters();

  /**
   * @deprecated Use {@link JavaFileCodeStyleFacade#forContext(PsiFile)} instead.
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static JavaCodeStyleSettingsFacade getInstance(Project project) {
    return project.getService(JavaCodeStyleSettingsFacade.class);
  }
}
