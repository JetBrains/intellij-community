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

import com.intellij.Patches;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.ide.CutElementMarker;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.EventDispatcher;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CopyPasteManagerEx extends CopyPasteManager implements ClipboardOwner {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.CopyPasteManagerEx");

  private final ArrayList<Transferable> myDatas;

//  private static long ourWastedMemory = 0;
//  private static long ourLastPrintedMemory = 0;
//  private static long ourLastPrintTime = 0;
//  private static long ourInvokationCounter = 0;

  private final EventDispatcher<ContentChangedListener> myDispatcher = EventDispatcher.create(ContentChangedListener.class);
  private static final int DELAY_UNTIL_ABORT_CLIPBOARD_ACCESS = 2000;
  private boolean myIsWarningShown = false;

  public static CopyPasteManagerEx getInstanceEx() {
    return (CopyPasteManagerEx)getInstance();
  }

  public CopyPasteManagerEx() {
    myDatas = new ArrayList<Transferable>();
  }

  public Transferable getSystemClipboardContents() {
    return getSystemClipboardContents(true);
  }

  public Transferable getSystemClipboardContents(boolean showMessage) {
    final Transferable[] contents = new Transferable[] {null};
    final boolean[] success = new boolean[] {false};
    Runnable accessor = new Runnable() {
      public void run() {
        try {
          for (int i = 0; i < 3; i++) {
            try {
              contents[0] = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(CopyPasteManagerEx.this);
            }
            catch (IllegalStateException e) {
              try {
                Thread.sleep(50);
              }
              catch (InterruptedException e1) {
              }
              continue;
            }
            break;
          }

          success[0] = true;
        }
        catch (Throwable e) {
          // No luck
        }        
        finally {
          Thread.interrupted(); // reset interrupted status
        }
      }
    };

    if (Patches.SUN_BUG_ID_4818143) {
      final Future<?> accessorFuture = ApplicationManager.getApplication().executeOnPooledThread(accessor);

      try {
        accessorFuture.get(DELAY_UNTIL_ABORT_CLIPBOARD_ACCESS, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException e) {
        // {no luck}
      }
      catch (TimeoutException e) {
        // {no luck}
      }
      catch (ExecutionException e) {
        LOG.error(e);
      }

      if (success[0]) return contents[0];
      accessorFuture.cancel(true);
      if (showMessage) {
        showWorkaroundMessage();
      } else {
        LOG.warn("Can't access to SystemClipboard");
      }

      return null;
    }
    else {
      accessor.run();
      return contents[0];
    }
  }

  private void showWorkaroundMessage() {
    if (myIsWarningShown) return;
    final String productName = ApplicationNamesInfo.getInstance().getProductName();
    Messages.showErrorDialog(IdeBundle.message("error.paste.bug.workaround", productName, productName), IdeBundle.message("title.system.error"));
    myIsWarningShown = true;
  }

//  private long getUsedMemory() {
//    Runtime runtime = Runtime.getRuntime();
//    long usedMemory = runtime.totalMemory() - runtime.freeMemory();
//    return usedMemory;
//  }
//
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

  public boolean isCutElement(final Object element) {
    for(CutElementMarker marker: Extensions.getExtensions(CutElementMarker.EP_NAME)) {
      if (marker.isCutElement(element)) return true;
    }
    return false;
  }

  void setSystemClipboardContent(final Transferable content) {
    final boolean[] success = new boolean[]{false};
    final Runnable accessor = new Runnable() {
      public void run() {
        try {
          for (int i = 0; i < 3; i++) {
            try {
              Toolkit.getDefaultToolkit().getSystemClipboard().setContents(content, CopyPasteManagerEx.this);
            }
            catch (IllegalStateException e) {
              try {
                Thread.sleep(50);
              }
              catch (InterruptedException e1) {
              }
              continue;
            }
            break;
          }
          success[0] = true;
        }
        finally {
          Thread.interrupted(); // reset interrupted status
        }
      }
    };

    if (Patches.SUN_BUG_ID_4818143) {
      Future<?> accessorFuture = ApplicationManager.getApplication().executeOnPooledThread(accessor);

      try {
        accessorFuture.get(DELAY_UNTIL_ABORT_CLIPBOARD_ACCESS, TimeUnit.MILLISECONDS);
      }
      catch (Exception e) { /* no luck */ }

      if (!success[0]) {
        showWorkaroundMessage();
        accessorFuture.cancel(true);
      }
    }
    else {
      accessor.run();
    }
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
