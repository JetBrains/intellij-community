// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.client.ClientAppSession;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.ide.CopyPasteManager.ContentChangedListener;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  static ClientCopyPasteManager getInstance(@NotNull ClientAppSession session) {
    return session.getService(ClientCopyPasteManager.class);
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

  void addContentChangedListener(@NotNull ContentChangedListener listener);

  void addContentChangedListener(@NotNull ContentChangedListener listener, @NotNull Disposable parentDisposable);

  void removeContentChangedListener(@NotNull ContentChangedListener listener);

  boolean isSystemSelectionSupported();

  @Nullable Transferable getSystemSelectionContents();

  void setSystemSelectionContents(@NotNull Transferable content);
}