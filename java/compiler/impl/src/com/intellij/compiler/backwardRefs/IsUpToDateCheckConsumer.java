// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.backwardRefs;

import com.intellij.openapi.compiler.CompileScope;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IntellijInternalApi;
import org.jetbrains.annotations.NotNull;

/**
 * Provides the result of {@link CompilerManager#isUpToDate(CompileScope)} once at project startup
 * if a suitable consumer exists.
 *
 * @see IsUpToDateCheckStartupActivity
 */
@IntellijInternalApi
public interface IsUpToDateCheckConsumer {
  ExtensionPointName<IsUpToDateCheckConsumer> EP_NAME = ExtensionPointName.create("com.intellij.compiler.isUpToDateChecker");

  /**
   * @param project
   * @return true if {@link CompilerManager#isUpToDate(CompileScope)} should be called
   */
  boolean isApplicable(@NotNull Project project);

  /**
   * Called if {@link IsUpToDateCheckConsumer#isApplicable(Project)}
   *
   * @param project
   * @param isUpToDate the result of {@link CompilerManager#isUpToDate(CompileScope)}
   */
  void isUpToDate(@NotNull Project project, boolean isUpToDate);
}
