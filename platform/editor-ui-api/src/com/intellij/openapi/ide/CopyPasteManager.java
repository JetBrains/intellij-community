// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.ui.Gray;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.EventListener;

public abstract class CopyPasteManager {
  public static final Color CUT_COLOR = Gray._160;

  public static CopyPasteManager getInstance() {
    return ApplicationManager.getApplication().getService(CopyPasteManager.class);
  }

  /**
   * @deprecated Please use overload with parent disposable
   */
  @Deprecated
  public abstract void addContentChangedListener(@NotNull ContentChangedListener listener);

  public abstract void addContentChangedListener(@NotNull ContentChangedListener listener, @NotNull Disposable parentDisposable);

  public abstract void removeContentChangedListener(@NotNull ContentChangedListener listener);

  public abstract boolean areDataFlavorsAvailable(DataFlavor @NotNull ... flavors);

  public abstract @Nullable Transferable getContents();

  public abstract @Nullable <T> T getContents(@NotNull DataFlavor flavor);

  public abstract Transferable @NotNull [] getAllContents();

  public abstract void setContents(@NotNull Transferable content);

  public abstract boolean isCutElement(@Nullable Object element);

  /**
   * We support 'kill rings' at the editor, i.e. every time when subsequent adjacent regions of text are copied they are
   * combined into single compound region. Every non-adjacent change makes existing regions unable to combine.
   * <p/>
   * However, there are situations when all 'kill rings' should be stopped manually (e.g. on undo). Hence, we need
   * a handle to ask for that. This method works like such a handle.
   *
   * @see KillRingTransferable
   */
  public abstract void stopKillRings();

  /**
   * Same as {@link #stopKillRings()}, but stops the 'kill rings' only if latest kill ring content came from the provided document.
   */
  public abstract void stopKillRings(@NotNull Document document);

  public interface ContentChangedListener extends EventListener {
    void contentChanged(final @Nullable Transferable oldTransferable, final Transferable newTransferable);
  }

  public static void copyTextToClipboard(@NotNull String text) {
    try {
      getInstance().setContents(new StringSelection(text));
    } catch (Exception ignore) { }
  }
}