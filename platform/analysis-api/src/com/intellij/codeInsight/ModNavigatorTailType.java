// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A base class for tail types that use {@link ModNavigator} instead of {@link Editor} to insert the tail.
 */
@ApiStatus.Experimental
public abstract class ModNavigatorTailType extends TailType {
  /**
   * @return true. {@link ModNavigatorTailType} should be always applicable. 
   * @deprecated If you want to make it non-applicable, simply do nothing inside {@link #processTail(Project, ModNavigator, int)}.
   * May become final in future.
   */
  @Deprecated
  @Override
  public boolean isApplicable(@NotNull InsertionContext context) {
    return true;
  }

  /**
   * @implSpec this implementation delegates to {@link #processTail(Project, ModNavigator, int)} adapting the arguments.
   * Normally, it should not be overridden in clients.
   */
  @Override
  public int processTail(final @NotNull Editor editor, int tailOffset) {
    return processTail(Objects.requireNonNull(editor.getProject()), editor.asModNavigator(), tailOffset);
  }

  /**
   * @param project current project
   * @param navigator {@link ModNavigator} to use 
   * @param tailOffset tail offset
   * @return new tail offset
   */
  public abstract int processTail(@NotNull Project project, @NotNull ModNavigator navigator, int tailOffset);
}
