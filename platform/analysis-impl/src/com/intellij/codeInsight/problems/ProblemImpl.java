/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.codeInsight.problems;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.problems.Problem;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class ProblemImpl implements Problem {
  private final VirtualFile virtualFile;
  private final HighlightInfo highlightInfo;
  private final boolean isSyntax;

  public ProblemImpl(@NotNull VirtualFile virtualFile, @NotNull HighlightInfo highlightInfo, final boolean isSyntax) {
    this.isSyntax = isSyntax;
    this.virtualFile = virtualFile;
    this.highlightInfo = highlightInfo;
  }

  @NotNull
  @Override
  public VirtualFile getVirtualFile() {
    return virtualFile;
  }

  public boolean isSyntaxOnly() {
    return isSyntax;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final ProblemImpl problem = (ProblemImpl)o;

    if (isSyntax != problem.isSyntax) return false;
    if (!highlightInfo.equals(problem.highlightInfo)) return false;
    if (!virtualFile.equals(problem.virtualFile)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = virtualFile.hashCode();
    result = 31 * result + highlightInfo.hashCode();
    result = 31 * result + (isSyntax ? 1 : 0);
    return result;
  }

  @NonNls
  public String toString() {
    return "Problem: " + highlightInfo;
  }
}
