// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.problems;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class ProblemImpl implements Problem {
  private final VirtualFile virtualFile;
  private final HighlightInfo highlightInfo;
  private final boolean isSyntax;

  public ProblemImpl(@NotNull VirtualFile virtualFile, @NotNull HighlightInfo highlightInfo, final boolean isSyntax) {
    this.isSyntax = isSyntax;
    this.virtualFile = virtualFile;
    this.highlightInfo = highlightInfo;
  }

  @Override
  public @NotNull VirtualFile getVirtualFile() {
    return virtualFile;
  }

  public boolean isSyntaxOnly() {
    return isSyntax;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ProblemImpl problem = (ProblemImpl)o;

    if (isSyntax != problem.isSyntax) return false;
    if (!highlightInfo.equals(problem.highlightInfo)) return false;
    if (!virtualFile.equals(problem.virtualFile)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result;
    result = virtualFile.hashCode();
    result = 31 * result + highlightInfo.hashCode();
    result = 31 * result + (isSyntax ? 1 : 0);
    return result;
  }

  @Override
  public @NonNls String toString() {
    return "Problem: " + highlightInfo;
  }
}
