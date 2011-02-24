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
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ide.CutElementMarker;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.EventDispatcher;

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

  void fireContentChanged(final Transferable oldTransferable) {
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

  public void setContents(Transferable content) {
    Transferable old = getContents();
    addNewContentToStack(content);

    setSystemClipboardContent(content);

    fireContentChanged(old);
  }

  public boolean isDataFlavorAvailable(DataFlavor dataFlavor) {
    return myClipboardSynchronizer.isDataFlavorAvailable(dataFlavor);
  }

  public boolean isCutElement(final Object element) {
    for(CutElementMarker marker: Extensions.getExtensions(CutElementMarker.EP_NAME)) {
      if (marker.isCutElement(element)) return true;
    }
    return false;
  }

  void setSystemClipboardContent(final Transferable content) {
    myClipboardSynchronizer.setContent(content, this);
  }

  private void addNewContentToStack(Transferable content) {
    try {
      String clipString = getStringContent(content);
      if (clipString != null) {
        Transferable same = null;
        for (Transferable old : myDatas) {
          if (clipString.equals(getStringContent(old))) {
            same = old;
            break;
          }
        }

        if (same == null) {
          myDatas.add(0, content);
          deleteAfterAllowedMaximum();
        }
        else {
          moveContentTopStackTop(same);
        }
      }
    } catch (UnsupportedFlavorException e) {
    } catch (IOException e) {
    }
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
