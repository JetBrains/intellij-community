// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

@ApiStatus.Internal
public final class LocalCopyPasteManager extends CopyPasteManagerWithHistory {
  @Override
  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    ClipboardSynchronizer.getInstance().resetContent();
    super.lostOwnership(clipboard, contents);
  }

  @Override
  public boolean areDataFlavorsAvailable(DataFlavor @NotNull ... flavors) {
    return flavors.length > 0 && ClipboardSynchronizer.getInstance().areDataFlavorsAvailable(flavors);
  }

  @Override
  public Transferable getContents() {
    return ClipboardSynchronizer.getInstance().getContents();
  }

  @Override
  public @Nullable <T> T getContents(@NotNull DataFlavor flavor) {
    if (areDataFlavorsAvailable(flavor)) {
      //noinspection unchecked
      return (T)ClipboardSynchronizer.getInstance().getData(flavor);
    }
    return null;
  }

  @Override
  protected void doSetSystemClipboardContent(Transferable content) {
    ClipboardSynchronizer.getInstance().setContent(content, this);
  }
}