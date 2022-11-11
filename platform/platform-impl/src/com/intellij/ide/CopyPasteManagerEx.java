// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ide.CutElementMarker;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.LinkedListWithSum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.function.Function;
import java.util.function.Predicate;

public class CopyPasteManagerEx extends CopyPasteManager implements ClipboardOwner {
  private final EventDispatcher<ContentChangedListener> myDispatcher = EventDispatcher.create(ContentChangedListener.class);

  public static CopyPasteManagerEx getInstanceEx() {
    return (CopyPasteManagerEx)getInstance();
  }

  @Override
  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    ClientCopyPasteManager.getCurrentInstance().lostOwnership(clipboard, contents);
  }

  void fireContentChanged(@Nullable Transferable oldContent, @Nullable Transferable newContent) {
    myDispatcher.getMulticaster().contentChanged(oldContent, newContent);
  }

  @Override
  public void addContentChangedListener(@NotNull ContentChangedListener listener) {
    myDispatcher.addListener(listener);
  }

  @Override
  public void addContentChangedListener(@NotNull final ContentChangedListener listener, @NotNull Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  @Override
  public void removeContentChangedListener(@NotNull ContentChangedListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public boolean areDataFlavorsAvailable(DataFlavor @NotNull ... flavors) {
    return ClientCopyPasteManager.getCurrentInstance().areDataFlavorsAvailable(flavors);
  }

  @Override
  public void setContents(@NotNull Transferable content) {
    ClientCopyPasteManager.getCurrentInstance().setContents(content);
  }

  @Override
  public boolean isCutElement(@Nullable final Object element) {
    for (CutElementMarker marker : CutElementMarker.EP_NAME.getExtensionList()) {
      if (marker.isCutElement(element)) return true;
    }
    return false;
  }

  @Override
  public void stopKillRings() {
    ClientCopyPasteManager.getCurrentInstance().stopKillRings();
  }

  @Override
  public void stopKillRings(@NotNull Document document) {
    ClientCopyPasteManager.getCurrentInstance().stopKillRings(document);
  }

  @Override
  public @Nullable Transferable getContents() {
    return ClientCopyPasteManager.getCurrentInstance().getContents();
  }

  @Override
  public <T> @Nullable T getContents(@NotNull DataFlavor flavor) {
    return ClientCopyPasteManager.getCurrentInstance().getContents(flavor);
  }

  @Override
  public Transferable @NotNull [] getAllContents() {
    return ClientCopyPasteManager.getCurrentInstance().getAllContents();
  }

  public void removeContent(Transferable t) {
    ClientCopyPasteManager.getCurrentInstance().removeContent(t);
  }
  boolean removeIf(@NotNull Predicate<? super Transferable> predicate) {
    return ClientCopyPasteManager.getCurrentInstance().removeIf(predicate);
  }
  public void moveContentToStackTop(Transferable t) {
    ClientCopyPasteManager.getCurrentInstance().moveContentToStackTop(t);
  }

  public static <T> void deleteAfterAllowedMaximum(@NotNull LinkedListWithSum<T> data, int maxCount, int maxMemory,
                                                   @NotNull Function<? super T, ? extends T> purgedItemFactory) {
    int smallItemSizeLimit = maxMemory / maxCount / 10;

    if (data.size() > maxCount) {
      data.subList(maxCount, data.size()).clear();
    }

    LinkedListWithSum<T>.ListIterator it = data.listIterator(data.size());
    while (data.getSum() > maxMemory && it.hasPrevious() && it.previousIndex() > 0) {
      T purgedItem = it.previous();
      if (it.getValue() > smallItemSizeLimit) {
        it.set(purgedItemFactory.apply(purgedItem));
      }
    }
  }
}