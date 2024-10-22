// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;

@ApiStatus.Internal
public class LocalCopyPasteManager extends CopyPasteManagerWithHistory {
  private static final Logger LOG = Logger.getInstance(LocalCopyPasteManager.class);
  private final ClipboardOwner mySelectionOwner = (clipboard, contents) -> lostSelectionOwnership(contents);

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

  @Override
  public boolean isSystemSelectionSupported() {
    return Toolkit.getDefaultToolkit().getSystemSelection() != null;
  }

  @Override
  public @Nullable Transferable getSystemSelectionContents() {
    Clipboard selection = Toolkit.getDefaultToolkit().getSystemSelection();
    if (selection != null) {
      try {
        return selection.getContents(null);
      }
      catch (Exception e) {
        LOG.info(e);
      }
    }
    return null;
  }

  @Override
  public void setSystemSelectionContents(@NotNull Transferable content) {
    Clipboard selection = Toolkit.getDefaultToolkit().getSystemSelection();
    if (selection != null) {
      selection.setContents(content, mySelectionOwner /* a lambda here doesn't work as expected, as its identity might be not constant */);
    }
  }

  protected void lostSelectionOwnership(Transferable content) {}
}