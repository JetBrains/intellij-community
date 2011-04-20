/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

public class CopyPasteManagerEx extends CopyPasteManager implements ClipboardOwner {
  private final ArrayList<Transferable> myDatas;
  private final EventDispatcher<ContentChangedListener> myDispatcher = EventDispatcher.create(ContentChangedListener.class);
  private final ClipboardSynchronizer myClipboardSynchronizer;

  public static CopyPasteManagerEx getInstanceEx() {
    return (CopyPasteManagerEx)getInstance();
  }

  public CopyPasteManagerEx(ClipboardSynchronizer clipboardSynchronizer) {
    myClipboardSynchronizer = clipboardSynchronizer;
    myDatas = new ArrayList<Transferable>();
  }

  public Transferable getSystemClipboardContents() {
    return myClipboardSynchronizer.getContents();
  }

  public void lostOwnership(Clipboard clipboard, Transferable contents) {
    fireContentChanged(null);
  }

  void fireContentChanged(@Nullable final Transferable oldTransferable) {
    myDispatcher.getMulticaster().contentChanged(oldTransferable, getContents());
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

  public void setContents(@NotNull final Transferable content) {
    Transferable old = getContents();
    Transferable contentToUse = addNewContentToStack(content);

    setSystemClipboardContent(contentToUse);

    fireContentChanged(old);
  }

  public boolean isDataFlavorAvailable(DataFlavor dataFlavor) {
    return myClipboardSynchronizer.isDataFlavorAvailable(dataFlavor);
  }

  public boolean isCutElement(@Nullable final Object element) {
    for(CutElementMarker marker: Extensions.getExtensions(CutElementMarker.EP_NAME)) {
      if (marker.isCutElement(element)) return true;
    }
    return false;
  }

  @Override
  public void stopKillRings() {
    for (Transferable data : myDatas) {
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
        if (killRingContent.isReadyToCombine() && !myDatas.isEmpty()) {
          Transferable prev = myDatas.get(0);
          if (prev instanceof KillRingTransferable) {
            Transferable merged = merge(killRingContent, (KillRingTransferable)prev);
            if (merged != null) {
              myDatas.set(0, merged);
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
      for (Transferable old : myDatas) {
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
    } catch (UnsupportedFlavorException e) {
    } catch (IOException e) {
    }
    return content;
  }

  private void addToTheTopOfTheStack(@NotNull Transferable content) {
    myDatas.add(0, content);
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
    for (int i = myDatas.size() - 1; i >= max; i--) {
      myDatas.remove(i);
    }
  }

  public Transferable getContents() {
    return getSystemClipboardContents();
  }

  public Transferable[] getAllContents() {
    deleteAfterAllowedMaximum();

    Transferable content = getSystemClipboardContents();
    if (content != null) {
      try {
        String clipString = getStringContent(content);
        String datasString = null;

        if (!myDatas.isEmpty()) {
          datasString = getStringContent(myDatas.get(0));
        }

        if (clipString != null && clipString.length() > 0 && !Comparing.equal(clipString, datasString)) {
          myDatas.add(0, content);
        }
      } catch (UnsupportedFlavorException e) {
      } catch (IOException e) {
      }
    }

    return myDatas.toArray(new Transferable[myDatas.size()]);
  }

  public void removeContent(Transferable t) {
    Transferable old = getContents();
    boolean isCurrentClipboardContent = myDatas.indexOf(t) == 0;
    myDatas.remove(t);
    if (isCurrentClipboardContent) {
      if (!myDatas.isEmpty()) {
        setSystemClipboardContent(myDatas.get(0));
      }
      else {
        setSystemClipboardContent(new StringSelection(""));
      }
    }
    fireContentChanged(old);
  }

  public void moveContentTopStackTop(Transferable t) {
    setSystemClipboardContent(t);
    myDatas.remove(t);
    myDatas.add(0, t);
  }
}
