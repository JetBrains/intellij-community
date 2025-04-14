// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.problems;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.problems.Problem;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

@ApiStatus.Internal
public final class ProblemImpl implements Problem {
  private final VirtualFile virtualFile;
  private final boolean isSyntax;
  private final @NotNull TextRange myTextRange;
  private final @NotNull HighlightSeverity mySeverity;
  private final @NotNull HighlightInfoType myType;
  private final @NlsContexts.DetailedDescription String myDescription;
  private final TextAttributes myForcedTextAttributes;
  private final TextAttributesKey myForcedTextAttributesKey;

  public ProblemImpl(@NotNull VirtualFile virtualFile, @NotNull HighlightInfo info, boolean isSyntax) {
    this.isSyntax = isSyntax;
    this.virtualFile = virtualFile;
    myTextRange = TextRange.create(info);
    mySeverity = info.getSeverity();
    myType = info.type;
    myDescription = info.getDescription();
    myForcedTextAttributes = info.forcedTextAttributes;
    myForcedTextAttributesKey = info.forcedTextAttributesKey;
  }

  @Override
  public @NotNull VirtualFile getVirtualFile() {
    return virtualFile;
  }

  public boolean isSyntaxOnly() {
    return isSyntax;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProblemImpl problem)) return false;

    return isSyntax == problem.isSyntax &&
           virtualFile.equals(problem.virtualFile) &&
           myTextRange.equals(problem.myTextRange) &&
           mySeverity.equals(problem.mySeverity) &&
           myType.equals(problem.myType) &&
           Objects.equals(myDescription, problem.myDescription) &&
           Objects.equals(myForcedTextAttributes, problem.myForcedTextAttributes) &&
           Objects.equals(myForcedTextAttributesKey, problem.myForcedTextAttributesKey);
  }

  @Override
  public int hashCode() {
    int result;
    result = virtualFile.hashCode();
    result = 31 * result + myTextRange.hashCode();
    result = 31 * result + (isSyntax ? 1 : 0);
    return result;
  }

  @Override
  public @NonNls String toString() {
    return "Problem: " + myTextRange + ": "+myDescription;
  }
}
