// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.chains;

import com.intellij.diff.DiffDialogHints;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a list of changed files (ex: singular commit).
 * The list is not supposed to be changed and can be shown multiple times.
 * <p>
 * Use {@link SimpleDiffRequestChain} and {@link com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain} as typical implementations.
 * <p>
 * Use {@link AsyncDiffRequestChain} instead if loading the list of changed files is a slow operation
 *
 * @see DiffRequestSelectionChain
 * @see com.intellij.diff.DiffManager#showDiff(Project, DiffRequestChain, DiffDialogHints)
 * @see com.intellij.diff.impl.CacheDiffRequestChainProcessor
 */
public interface DiffRequestChain extends UserDataHolder {
  /**
   * NB: if you're calling this method for an unknown chain type, you should be ready to handle {@link AsyncDiffRequestChain}.
   */
  @NotNull
  @RequiresEdt
  List<? extends DiffRequestProducer> getRequests();

  @RequiresEdt
  int getIndex();

  /**
   * @see com.intellij.diff.impl.CacheDiffRequestChainProcessor#setCurrentRequest
   * @deprecated This method will not change selected position if chain was already shown.
   */
  @Deprecated(forRemoval = true)
  @RequiresEdt
  default void setIndex(int index) {
  }
}
