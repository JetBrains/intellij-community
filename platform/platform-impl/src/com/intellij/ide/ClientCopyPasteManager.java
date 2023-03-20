// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.function.Predicate;

/**
 * A per-client service managing clipboard.
 * @see CopyPasteManagerEx
 */
@ApiStatus.Internal
public interface ClientCopyPasteManager extends ClipboardOwner {
  static ClientCopyPasteManager getCurrentInstance() {
    return ApplicationManager.getApplication().getService(ClientCopyPasteManager.class);
  }

  boolean areDataFlavorsAvailable(DataFlavor @NotNull... flavors);

  void setContents(@NotNull Transferable content);
  Transferable getContents();
  <T> T getContents(@NotNull DataFlavor flavor);

  void stopKillRings();

  void stopKillRings(@NotNull Document document);
  Transferable @NotNull [] getAllContents();

  void removeContent(Transferable t);

  void moveContentToStackTop(Transferable t);

  boolean removeIf(@NotNull Predicate<? super Transferable> predicate);
}