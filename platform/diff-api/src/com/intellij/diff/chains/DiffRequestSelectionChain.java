// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.chains;

import com.intellij.openapi.ListSelection;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Represents a chain that is backed by {@link ListSelection} class.
 */
public interface DiffRequestSelectionChain extends DiffRequestChain {
  @NotNull
  @RequiresEdt
  ListSelection<? extends DiffRequestProducer> getListSelection();

  @Override
  default @NotNull List<? extends DiffRequestProducer> getRequests() {
    return getListSelection().getList();
  }

  @Override
  default int getIndex() {
    return getListSelection().getSelectedIndex();
  }
}
