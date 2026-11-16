// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.java.JavaBundle;
import com.intellij.refactoring.modplan.ModPlanOption;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * The options of the Java rename, besides those of {@link com.intellij.refactoring.modplan.rename.ModPlanRenameOptions}.
 */
@ApiStatus.Experimental
public final class ModPlanJavaRenameOptions {
  /**
   * The answer of the question "the method overrides a method, rename the base method instead?": on, the base method is renamed with its
   * hierarchy, as the headless rename does; off, the method is renamed alone.
   */
  public static final @NotNull ModPlanOption<Boolean> RENAME_BASE_METHOD =
    ModPlanOption.bool("renameBaseMethod", true, JavaBundle.messagePointer("button.base.method", 1));

  private ModPlanJavaRenameOptions() {
  }
}
