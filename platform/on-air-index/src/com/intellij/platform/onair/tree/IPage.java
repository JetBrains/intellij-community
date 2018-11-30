// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.platform.onair.tree;

import com.intellij.platform.onair.storage.api.KeyValueConsumer;
import com.intellij.platform.onair.storage.api.Novelty;
import com.intellij.platform.onair.tree.functional.BaseTransientPage;
import com.intellij.platform.onair.tree.functional.TransientBTreePrototype;
import org.jetbrains.annotations.NotNull;

public interface IPage {
  // meta

  int getSize();

  boolean isBottom();

  boolean isTransient();

  // TODO: cleanup?

  long getMutableAddress();

  BaseTransientPage getTransientCopy(@NotNull Novelty.Accessor novelty,
                                     @NotNull TransientBTreePrototype tree,
                                     long epoch);

  // crud

  byte[] getMinKey();

  byte[] get(Novelty.Accessor novelty, byte[] key);

  boolean forEach(Novelty.Accessor novelty, KeyValueConsumer consumer);

  boolean forEach(Novelty.Accessor novelty, byte[] key, KeyValueConsumer consumer);

  // tree-specific methods

  void insertDirectly(@NotNull Novelty.Accessor novelty, final int pos, @NotNull byte[] key, Object child);

  IPage split(@NotNull Novelty.Accessor novelty, int from, int length);

  // save

  void flush(@NotNull Novelty.Accessor novelty);
}
