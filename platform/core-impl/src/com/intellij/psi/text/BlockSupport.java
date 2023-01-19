// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.text;

import com.intellij.lang.ASTNode;
import com.intellij.lang.FileASTNode;
import com.intellij.openapi.diagnostic.ControlFlowException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DiffLog;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class BlockSupport {
  public static BlockSupport getInstance(Project project) {
    return project.getService(BlockSupport.class);
  }

  public abstract void reparseRange(@NotNull PsiFile file, int startOffset, int endOffset, @NonNls @NotNull CharSequence newText) throws IncorrectOperationException;

  @NotNull
  public abstract DiffLog reparseRange(@NotNull PsiFile file,
                                       @NotNull FileASTNode oldFileNode,
                                       @NotNull TextRange changedPsiRange,
                                       @NotNull CharSequence newText,
                                       @NotNull ProgressIndicator progressIndicator,
                                       @NotNull CharSequence lastCommittedText) throws IncorrectOperationException;

  public static final Key<Boolean> DO_NOT_REPARSE_INCREMENTALLY = Key.create("DO_NOT_REPARSE_INCREMENTALLY");
  public static final Key<Pair<ASTNode, CharSequence>> TREE_TO_BE_REPARSED = Key.create("TREE_TO_BE_REPARSED");

  public static class ReparsedSuccessfullyException extends RuntimeException implements ControlFlowException {
    private final DiffLog myDiffLog;

    public ReparsedSuccessfullyException(@NotNull DiffLog diffLog) {
      myDiffLog = diffLog;
    }

    @NotNull
    public DiffLog getDiffLog() {
      return myDiffLog;
    }

    @NotNull
    @Override
    public synchronized Throwable fillInStackTrace() {
      return this;
    }
  }

  // maximal tree depth for which incremental reparse is allowed
  // if tree is deeper then it will be replaced completely - to avoid SOEs
  public static final int INCREMENTAL_REPARSE_DEPTH_LIMIT = Registry.intValue("psi.incremental.reparse.depth.limit");

  public static final Key<Boolean> TREE_DEPTH_LIMIT_EXCEEDED = Key.create("TREE_IS_TOO_DEEP");

  public static boolean isTooDeep(UserDataHolder element) {
    return element != null && Boolean.TRUE.equals(element.getUserData(TREE_DEPTH_LIMIT_EXCEEDED));
  }
}
