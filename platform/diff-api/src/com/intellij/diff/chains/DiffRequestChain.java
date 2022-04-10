// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.chains;

import com.intellij.diff.DiffDialogHints;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents list of changed files (ex: singular commit).
 * The list is not supposed to be changed and can be shown multiple times.
 * <p>
 * Use {@link AsyncDiffRequestChain} to load requests asynchronously after showing UI
 * Use {@link com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain} for chains with common "Go to change" navigation popup.
 *
 * @see DiffRequestChainBase
 * @see com.intellij.diff.DiffManager#showDiff(Project, DiffRequestChain, DiffDialogHints)
 * @see com.intellij.diff.impl.CacheDiffRequestChainProcessor
 */
public interface DiffRequestChain extends UserDataHolder {
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
  void setIndex(int index);
}
