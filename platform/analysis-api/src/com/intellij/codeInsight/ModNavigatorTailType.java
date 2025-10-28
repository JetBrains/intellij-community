// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ModNavigator;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * A base class for tail types that use {@link ModNavigator} instead of {@link Editor} to insert the tail.
 */
public abstract class ModNavigatorTailType extends TailType {
  @Override
  public int processTail(final @NotNull Editor editor, int tailOffset) {
    return processTail(Objects.requireNonNull(editor.getProject()), editor.asPsiNavigator(), tailOffset);
  }

  /**
   * @param project current project
   * @param navigator {@link ModNavigator} to use 
   * @param tailOffset tail offset
   * @return new tail offset
   */
  public abstract int processTail(@NotNull Project project, @NotNull ModNavigator navigator, int tailOffset);
}
