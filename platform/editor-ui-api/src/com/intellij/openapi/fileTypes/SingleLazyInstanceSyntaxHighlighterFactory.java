// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public abstract class SingleLazyInstanceSyntaxHighlighterFactory extends SyntaxHighlighterFactory {
  private SyntaxHighlighter myValue;

  @Override
  public final @NotNull SyntaxHighlighter getSyntaxHighlighter(final Project project, final VirtualFile virtualFile) {
    if (myValue == null) {
      myValue = createHighlighter();
    }
    return myValue;
  }

  protected abstract @NotNull SyntaxHighlighter createHighlighter();
}