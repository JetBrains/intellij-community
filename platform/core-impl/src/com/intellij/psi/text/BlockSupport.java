// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provides mechanisms to perform incremental reparsing of PSI trees.
 */
public abstract class BlockSupport {

  public static BlockSupport getInstance(@NotNull Project project) {
    return project.getService(BlockSupport.class);
  }

  /**
   * Reparses a specified range in the given file with the provided new text.
   * The operation adjusts the PSI tree accordingly for the specified text modifications.
   * Throws an {@link IncorrectOperationException} if the operation cannot be successfully completed.
   *
   * @param file the PSI file in which a range of text is to be reparsed. Must not be null.
   * @param startOffset the starting offset of the range to be reparsed, inclusive.
   * @param endOffset the ending offset of the range to be reparsed, exclusive.
   * @param newText the new text to replace the range with. Must not be null.
   * @throws IncorrectOperationException if the specified reparse operation cannot be completed.
   */
  public abstract void reparseRange(@NotNull PsiFile file,
                                    int startOffset,
                                    int endOffset,
                                    @NonNls @NotNull CharSequence newText) throws IncorrectOperationException;

  /**
   * Reparses the specified range of PSI content in the given file with the provided new text,
   * updating the PSI structure based on the changes and generating a diff log.
   * This method supports incremental reparsing where applicable, otherwise it performs a full reparse.
   * Throws an {@link IncorrectOperationException} if the operation cannot be successfully completed.
   *
   * @param file the PSI file in which a range of text is to be reparsed. Must not be null.
   * @param oldFileNode the original AST node of the file before parsing. Must not be null.
   * @param changedPsiRange the range of PSI text that has been changed and needs reparsing. Must not be null.
   * @param newText the new content to be inserted or altered in the specified range. Must not be null.
   * @param progressIndicator the progress indicator to monitor the status of the reparse operation. Must not be null.
   * @param lastCommittedText the state of the file's text before the modification, typically the last committed version. Must not be null.
   * @return a {@link DiffLog} object that contains the differences and updates resulting from the reparse.
   * @throws IncorrectOperationException if the reparse operation cannot be successfully completed.
   */
  public abstract @NotNull DiffLog reparseRange(@NotNull PsiFile file,
                                                @NotNull FileASTNode oldFileNode,
                                                @NotNull TextRange changedPsiRange,
                                                @NotNull CharSequence newText,
                                                @NotNull ProgressIndicator progressIndicator,
                                                @NotNull CharSequence lastCommittedText) throws IncorrectOperationException;

  /**
   * Determines whether the provided element has exceeded a predefined tree depth limit.
   *
   * @param element the element to check for the TREE_DEPTH_LIMIT_EXCEEDED user data. Can be null.
   * @return {@code true} if the user data TREE_DEPTH_LIMIT_EXCEEDED is present and set to {@code true} in the element; {@code false} otherwise.
   */
  public static boolean isTooDeep(@Nullable UserDataHolder element) {
    return element != null && Boolean.TRUE.equals(element.getUserData(TREE_DEPTH_LIMIT_EXCEEDED));
  }

  /**
   * A specialized exception indicating a successful reparsing of the PSI tree.
   * This exception is part of the control flow and should not be logged or caught.
   * <p>
   * Instances of this exception encapsulate a {@link DiffLog}, which contains the details of the changes resulting from the reparse
   * operation.
   * The class is not marked {@link ApiStatus.Internal} to allow rethrowing this exception in plugin code.
   */
  public static final class ReparsedSuccessfullyException extends RuntimeException implements ControlFlowException {
    private final DiffLog myDiffLog;

    @ApiStatus.Internal
    public ReparsedSuccessfullyException(@NotNull DiffLog diffLog) {
      myDiffLog = diffLog;
    }

    @ApiStatus.Internal
    public @NotNull DiffLog getDiffLog() {
      return myDiffLog;
    }

    @Override
    public synchronized @NotNull Throwable fillInStackTrace() {
      return this;
    }
  }

  /**
   * A key used to indicate that a file should not be reparsed incrementally because it's too big.
   */
  @ApiStatus.Internal
  public static final Key<Boolean> DO_NOT_REPARSE_INCREMENTALLY = Key.create("DO_NOT_REPARSE_INCREMENTALLY");

  /**
   * A key used by {@link BlockSupport} and {@link com.intellij.lang.PsiBuilder} to communicate reparsing.
   * It's not intended to be used by plugins.
   */
  public static final Key<Pair<ASTNode, CharSequence>> TREE_TO_BE_REPARSED = Key.create("TREE_TO_BE_REPARSED");

  /**
   * maximal tree depth for which incremental reparse is allowed
   * if tree is deeper, it will be replaced completely - to avoid SOEs
   */
  @ApiStatus.Internal
  public static final int INCREMENTAL_REPARSE_DEPTH_LIMIT = Registry.intValue("psi.incremental.reparse.depth.limit");

  /**
   * An internal key used by {@link BlockSupport} and {@link com.intellij.lang.PsiBuilder} to communicate that a given tree (PsiFile or
   * a chameleon) is too deep. If a tree is too deep, reparsing stops working for it.
   */
  @ApiStatus.Internal
  public static final Key<Boolean> TREE_DEPTH_LIMIT_EXCEEDED = Key.create("TREE_IS_TOO_DEEP");
}
