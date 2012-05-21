/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ide.CutElementMarker;
import com.intellij.openapi.ide.KillRingTransferable;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.EventDispatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CopyPasteManagerEx extends CopyPasteManager implements ClipboardOwner {
  private final List<Transferable> myData = new ArrayList<Transferable>();
  private final EventDispatcher<ContentChangedListener> myDispatcher = EventDispatcher.create(ContentChangedListener.class);
  private final ClipboardSynchronizer myClipboardSynchronizer;

  public static CopyPasteManagerEx getInstanceEx() {
    return (CopyPasteManagerEx)getInstance();
  }

  public CopyPasteManagerEx(ClipboardSynchronizer clipboardSynchronizer) {
    myClipboardSynchronizer = clipboardSynchronizer;
  }

  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    myClipboardSynchronizer.resetContent();
    fireContentChanged(contents, null);
  }

  void fireContentChanged(@Nullable final Transferable oldTransferable, @Nullable final Transferable _new) {
    myDispatcher.getMulticaster().contentChanged(oldTransferable, _new);
  }

  public void addContentChangedListener(ContentChangedListener listener) {
    myDispatcher.addListener(listener);
  }

  public void addContentChangedListener(final ContentChangedListener listener, Disposable parentDisposable) {
    myDispatcher.addListener(listener, parentDisposable);
  }

  public void removeContentChangedListener(ContentChangedListener listener) {
    myDispatcher.removeListener(listener);
  }

  @Override
  public boolean isDataFlavorAvailable(@Nullable DataFlavor flavor) {
    return flavor != null && myClipboardSynchronizer.isDataFlavorAvailable(flavor);
  }

  public void setContents(@NotNull final Transferable content) {
    Transferable old = getContents();
    Transferable contentToUse = addNewContentToStack(content);

    setSystemClipboardContent(contentToUse);

    fireContentChanged(old, contentToUse);
  }

  public boolean isCutElement(@Nullable final Object element) {
    for(CutElementMarker marker: Extensions.getExtensions(CutElementMarker.EP_NAME)) {
      if (marker.isCutElement(element)) return true;
    }
    return false;
  }

  @Override
  public void stopKillRings() {
    for (Transferable data : myData) {
      if (data instanceof KillRingTransferable) {
        ((KillRingTransferable)data).setReadyToCombine(false);
      }
    }
  }

  void setSystemClipboardContent(final Transferable content) {
    myClipboardSynchronizer.setContent(content, this);
  }

  /**
   * Stores given content within the current manager. It is merged with already stored ones
   * if necessary (see {@link KillRingTransferable}).
   * 
   * @param content     content to store
   * @return            content that is either the given one or the one that was assembled from it and already stored one
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
      
      Transferable same = null;
      for (Transferable old : myData) {
        if (clipString.equals(getStringContent(old))) {
          same = old;
          break;
        }
      }

      if (same == null) {
        addToTheTopOfTheStack(content);
      }
      else {
        moveContentTopStackTop(same);
      }
    }
    catch (UnsupportedFlavorException ignore) { }
    catch (IOException ignore) { }
    return content;
  }

  private void addToTheTopOfTheStack(@NotNull Transferable content) {
    myData.add(0, content);
    deleteAfterAllowedMaximum();
  }
  
  /**
   * Merges given new data with the given old one and returns merge result in case of success.
   * 
   * @param newData     new data to merge
   * @param oldData     old data to merge
   * @return            merge result of the given data if possible; <code>null</code> otherwise
   * @throws IOException                  as defined by {@link Transferable#getTransferData(DataFlavor)}
   * @throws UnsupportedFlavorException   as defined by {@link Transferable#getTransferData(DataFlavor)}
   */
  @Nullable
  private static Transferable merge(@NotNull KillRingTransferable newData, @NotNull KillRingTransferable oldData)
    throws IOException, UnsupportedFlavorException
  {
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
  
  private static String getStringContent(Transferable content) throws UnsupportedFlavorException, IOException {
    return (String) content.getTransferData(DataFlavor.stringFlavor);
  }

  private void deleteAfterAllowedMaximum() {
    int max = UISettings.getInstance().MAX_CLIPBOARD_CONTENTS;
    for (int i = myData.size() - 1; i >= max; i--) {
      myData.remove(i);
    }
  }

  public Transferable getContents() {
    return myClipboardSynchronizer.getContents();
  }

  public Transferable[] getAllContents() {
    deleteAfterAllowedMaximum();

    Transferable content = getContents();
    if (content != null) {
      try {
        String clipString = getStringContent(content);
        String dataString = null;

        if (!myData.isEmpty()) {
          dataString = getStringContent(myData.get(0));
        }

        if (clipString != null && clipString.length() > 0 && !Comparing.equal(clipString, dataString)) {
          myData.add(0, content);
        }
      }
      catch (UnsupportedFlavorException ignore) { }
      catch (IOException ignore) { }
    }

    return myData.toArray(new Transferable[myData.size()]);
  }

  public void removeContent(Transferable t) {
    Transferable old = getContents();
    boolean isCurrentClipboardContent = myData.indexOf(t) == 0;
    myData.remove(t);
    Transferable _new = null;
    if (isCurrentClipboardContent) {
      if (!myData.isEmpty()) {
        _new = myData.get(0); 
        setSystemClipboardContent(_new);
      }
      else {
        _new = new StringSelection("");
        setSystemClipboardContent(_new);
      }
    }
    fireContentChanged(old, _new);
  }

  public void moveContentTopStackTop(Transferable t) {
    setSystemClipboardContent(t);
    myData.remove(t);
    myData.add(0, t);
  }
}
