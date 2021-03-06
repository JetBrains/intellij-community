// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.CaretStateTransferableData;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ide.CutElementMarker;
import com.intellij.openapi.ide.KillRingTransferable;
import com.intellij.openapi.ide.Sizeable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.UIBundle;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.LinkedListWithSum;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import java.util.function.Function;

/**
 * This implementation attempts to limit memory occupied by clipboard history. To make it work, {@link Transferable} instances passed to
 * {@link #setContents(Transferable)} should implement {@link Sizeable} interface. See {@link #getSize(Transferable)} method for details on
 * estimating the size of {@code Transferable} and {@link #deleteAfterAllowedMaximum()} method for history trimming logic.
 */
public class CopyPasteManagerEx extends CopyPasteManager implements ClipboardOwner {
  private static final Logger LOG = Logger.getInstance(CopyPasteManagerEx.class);

  private final LinkedListWithSum<Transferable> myData = new LinkedListWithSum<>(CopyPasteManagerEx::getSize);
  private final EventDispatcher<ContentChangedListener> myDispatcher = EventDispatcher.create(ContentChangedListener.class);
  private boolean myOwnContent;

  public static CopyPasteManagerEx getInstanceEx() {
    return (CopyPasteManagerEx)getInstance();
  }

  @Override
  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    myOwnContent = false;
    ClipboardSynchronizer.getInstance().resetContent();
    fireContentChanged(contents, null);
  }

  private void fireContentChanged(@Nullable Transferable oldContent, @Nullable Transferable newContent) {
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
    return flavors.length > 0 && ClipboardSynchronizer.getInstance().areDataFlavorsAvailable(flavors);
  }

  @Override
  public void setContents(@NotNull Transferable content) {
    Transferable oldContent = myOwnContent && !myData.isEmpty() ? myData.get(0) : null;

    Transferable contentToUse = addNewContentToStack(content);
    setSystemClipboardContent(contentToUse);

    fireContentChanged(oldContent, contentToUse);
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
    if (LOG.isDebugEnabled()) {
      LOG.debug("Kill ring reset", new Throwable());
    }
    doStopKillRings();
  }

  @Override
  public void stopKillRings(@NotNull Document document) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("Kill ring reset for " + document, new Throwable());
    }
    if (!myData.isEmpty()) {
      Transferable top = myData.get(0);
      if (top instanceof KillRingTransferable && document == ((KillRingTransferable)top).getDocument()) {
        doStopKillRings();
      }
    }
  }

  private void doStopKillRings() {
    for (Transferable data : myData) {
      if (data instanceof KillRingTransferable) {
        ((KillRingTransferable)data).setReadyToCombine(false);
      }
    }
  }

  private void setSystemClipboardContent(Transferable content) {
    ClipboardSynchronizer.getInstance().setContent(content, this);
    myOwnContent = true;
  }

  /**
   * Stores given content within the current manager. It is merged with already stored ones
   * if necessary (see {@link KillRingTransferable}).
   *
   * @param content content to store
   * @return content that is either the given one or the one that was assembled from it and already stored one
   */
  @NotNull
  private Transferable addNewContentToStack(@NotNull Transferable content) {
    try {
      String clipString = getStringContent(content);
      if (clipString == null) {
        return content;
      }

      if (content instanceof KillRingTransferable) {
        KillRingTransferable killRingContent = (KillRingTransferable)content;
        if (killRingContent.isReadyToCombine() && !myData.isEmpty()) {
          Transferable prev = myData.get(0);
          if (prev instanceof KillRingTransferable) {
            Transferable merged = merge(killRingContent, (KillRingTransferable)prev);
            if (merged != null) {
              myData.set(0, merged);
              return merged;
            }
          }
        }
        if (killRingContent.isReadyToCombine()) {
          addToTheTopOfTheStack(killRingContent);
          return killRingContent;
        }
      }

      CaretStateTransferableData caretData = CaretStateTransferableData.getFrom(content);
      Iterator<Transferable> it = myData.iterator();
      while (it.hasNext()) {
        Transferable old = it.next();
        if (clipString.equals(getStringContent(old)) &&
            CaretStateTransferableData.areEquivalent(caretData, CaretStateTransferableData.getFrom(old))) {
          it.remove();
          myData.add(0, content);
          return content;
        }
      }

      addToTheTopOfTheStack(content);
    }
    catch (UnsupportedFlavorException | IOException ignore) { }
    return content;
  }

  private void addToTheTopOfTheStack(@NotNull Transferable content) {
    myData.add(0, content);
    deleteAfterAllowedMaximum();
  }

  /**
   * Merges given new data with the given old one and returns merge result in case of success.
   *
   * @param newData new data to merge
   * @param oldData old data to merge
   * @return merge result of the given data if possible; {@code null} otherwise
   * @throws IOException                as defined by {@link Transferable#getTransferData(DataFlavor)}
   * @throws UnsupportedFlavorException as defined by {@link Transferable#getTransferData(DataFlavor)}
   */
  @Nullable
  private static Transferable merge(@NotNull KillRingTransferable newData, @NotNull KillRingTransferable oldData)
    throws IOException, UnsupportedFlavorException {
    if (!oldData.isReadyToCombine() || !newData.isReadyToCombine()) {
      return null;
    }

    Document document = newData.getDocument();
    if (document == null || document != oldData.getDocument()) {
      return null;
    }

    Object newDataText = newData.getTransferData(DataFlavor.stringFlavor);
    Object oldDataText = oldData.getTransferData(DataFlavor.stringFlavor);
    if (newDataText == null || oldDataText == null) {
      return null;
    }

    if (oldData.isCut()) {
      if (newData.getStartOffset() == oldData.getStartOffset()) {
        return new KillRingTransferable(
          oldDataText.toString() + newDataText, document, oldData.getStartOffset(), newData.getEndOffset(), newData.isCut()
        );
      }
    }

    if (newData.getStartOffset() == oldData.getEndOffset()) {
      return new KillRingTransferable(
        oldDataText.toString() + newDataText, document, oldData.getStartOffset(), newData.getEndOffset(), false
      );
    }

    if (newData.getEndOffset() == oldData.getStartOffset()) {
      return new KillRingTransferable(
        newDataText.toString() + oldDataText, document, newData.getStartOffset(), oldData.getEndOffset(), false
      );
    }

    return null;
  }

  private static String getStringContent(@NotNull Transferable content) {
    try {
      return (String)content.getTransferData(DataFlavor.stringFlavor);
    }
    catch (UnsupportedFlavorException | IOException ignore) { }
    return null;
  }

  private void deleteAfterAllowedMaximum() {
    int maxCount = Math.max(1, Registry.intValue("clipboard.history.max.items"));
    int maxMemory = Math.max(0, Registry.intValue("clipboard.history.max.memory"));
    deleteAfterAllowedMaximum(myData, maxCount, maxMemory, item -> createPurgedItem());
  }

  @Override
  public Transferable getContents() {
    return ClipboardSynchronizer.getInstance().getContents();
  }

  @Nullable
  @Override
  public <T> T getContents(@NotNull DataFlavor flavor) {
    if (areDataFlavorsAvailable(flavor)) {
      //noinspection unchecked
      return (T)ClipboardSynchronizer.getInstance().getData(flavor);
    }
    return null;
  }

  @Override
  public Transferable @NotNull [] getAllContents() {
    String clipString = getContents(DataFlavor.stringFlavor);
    if (clipString != null && (myData.isEmpty() || !Objects.equals(clipString, getStringContent(myData.get(0))))) {
      addToTheTopOfTheStack(new StringSelection(clipString));
    }
    return myData.toArray(new Transferable[0]);
  }

  public void removeContent(Transferable t) {
    Transferable current = myData.isEmpty() ? null : myData.get(0);
    myData.remove(t);
    if (Comparing.equal(t, current)) {
      Transferable newContent = !myData.isEmpty() ? myData.get(0) : new StringSelection("");
      setSystemClipboardContent(newContent);
      fireContentChanged(current, newContent);
    }
  }

  public void moveContentToStackTop(Transferable t) {
    Transferable current = myData.isEmpty() ? null : myData.get(0);
    if (!Comparing.equal(t, current)) {
      myData.remove(t);
      myData.add(0, t);
      setSystemClipboardContent(t);
      fireContentChanged(current, t);
    }
    else {
      setSystemClipboardContent(t);
    }
  }

  private static Transferable createPurgedItem() {
    return new StringSelection(UIBundle.message("clipboard.history.purged.item"));
  }

  private static int getSize(Transferable t) {
    if (t instanceof StringSelection) {
      try {
        return StringUtil.length((String)t.getTransferData(DataFlavor.stringFlavor));
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    else if (t instanceof Sizeable) {
      int size = ((Sizeable)t).getSize();
      //noinspection ConstantConditions
      if (size >= 0) {
        return size;
      }
      LOG.error("Got negative size (" + size + ") from " + t);
    }
    return 1000; // some value for an unknown type
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