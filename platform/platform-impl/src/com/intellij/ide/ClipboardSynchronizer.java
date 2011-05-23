/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.IntegerType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is used to workaround the problem with getting clipboard contents (http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4818143).
 * Although this bug is marked as fixed actually Sun just set 10 seconds timeout for {@link java.awt.datatransfer.Clipboard#getContents(Object)}
 * method. So we perform synchronization with system clipboard on a separate thread and schedule it when IDEA frame is activated or Copy/Cut
 * action in Swing component is invoked
 *
 * @author nik
 */
public class ClipboardSynchronizer implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ClipboardSynchronizer");
  @NonNls private static final String DATA_TRANSFER_TIMEOUT_PROPERTY = "sun.awt.datatransfer.timeout";
  private AtomicBoolean mySynchronizationInProgress = new AtomicBoolean(false);

  private Transferable myCurrentContent;

  private final Object myLock = new Object();

  public static ClipboardSynchronizer getInstance() {
    return ApplicationManager.getApplication().getComponent(ClipboardSynchronizer.class);
  }

  @Override
  public void initComponent() {
    if (!Patches.SLOW_GETTING_CLIPBOARD_CONTENTS) return;

    if (System.getProperty(DATA_TRANSFER_TIMEOUT_PROPERTY) == null) {
      System.setProperty(DATA_TRANSFER_TIMEOUT_PROPERTY, "2000");
    }

    FrameStateManager.getInstance().addListener(new FrameStateListener() {
      @Override
      public void onFrameDeactivated() {
      }

      @Override
      public void onFrameActivated() {
        scheduleSynchronization();
      }
    });
  }

  public void replaceDefaultCopyPasteActions(UIDefaults defaults) {
    if (!Patches.SLOW_GETTING_CLIPBOARD_CONTENTS) return;

    //ensure that '.actionMap' properties are initialized
    new JTextField();
    new JPasswordField();
    new JTextArea();
    //noinspection UndesirableClassUsage
    new JTable();

    String[] textComponents = {"TextField", "PasswordField", "TextArea", "Table"};
    for (String name : textComponents) {
      final String key = name + ".actionMap";
      final ActionMap actionMap = (ActionMap)defaults.get(key);
      if (actionMap != null) {
        replaceAction(actionMap, TransferHandler.getCopyAction());
        replaceAction(actionMap, TransferHandler.getCutAction());
      }
      else {
        LOG.warn(key + " property not initialized");
      }
    }
    setupEditorPanes();
  }

  /**
   * Performs changes that make copy/cut from editor panes trigger system clipboard content synchronization.
   */
  private void setupEditorPanes() {
    try {
      Field field = DefaultEditorKit.class.getDeclaredField("defaultActions");
      field.setAccessible(true);
      Action[] actions = (Action[])field.get(null);
      for (int i = 0; i < actions.length; i++) {
        Action action = actions[i];
        if (DefaultEditorKit.copyAction.equals(action.getValue(Action.NAME))
              || DefaultEditorKit.cutAction.equals(action.getValue(Action.NAME)))
        {
          actions[i] = wrap(action);
        }
      }
    }
    catch (Exception e) {
      LOG.warn("Can't setup clipboard actions for editor pane kit", e);
    }
  }

  private void replaceAction(ActionMap actionMap, final Action action) {
    final String actionName = (String)action.getValue(Action.NAME);
    if (actionName != null) {
      actionMap.put(actionName, wrap(action));
    }
  }

  /**
   * Wraps given action to the new action that triggers system clipboard content synchronization in addition to the basic
   * functionality.
   * 
   * @param action      action to wrap
   * @return            wrapped action
   */
  private Action wrap(@NotNull final Action action) {
    return new AbstractAction(action.getValue(Action.NAME).toString()) {
      @Override
      public void actionPerformed(ActionEvent e) {
        action.actionPerformed(e);
        scheduleSynchronization();
      }
    };
  }

  public Transferable getContents() {
    if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS) {
      synchronized (myLock) {
        return myCurrentContent;
      }
    }
    try {
      return doGetContents();
    }
    catch (IllegalStateException e) {
      LOG.info(e);
      return null;
    }
  }

  public boolean isDataFlavorAvailable(DataFlavor dataFlavor) {
    if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS) {
      final Transferable contents = getContents();
      return contents != null && contents.isDataFlavorSupported(dataFlavor);
    }
    return Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(dataFlavor);
  }

  private void scheduleSynchronization() {
    final boolean inProgress = mySynchronizationInProgress.getAndSet(true);
    if (inProgress) return;

    final Application app = ApplicationManager.getApplication();
    app.executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          final Transferable content = doGetContents();
          synchronized (myLock) {
            myCurrentContent = content;
          }
        }
        catch (Throwable e) {
          LOG.info(e);
        }
        finally {
          mySynchronizationInProgress.set(false);
        }
      }
    });
  }

  @Override
  public void disposeComponent() {
  }

  private Transferable doGetContents() throws IllegalStateException {
    if (SystemInfo.isMac && Registry.is("ide.mac.useNativeClipboard")) {
      Transferable safe = getContentsSafe();
      if (safe != null) {
        return safe;
      }
    }


    IllegalStateException last = null;
    for (int i = 0; i < 3; i++) {
      try {
        return Toolkit.getDefaultToolkit().getSystemClipboard().getContents(this);
      }
      catch (IllegalStateException e) {
        try {
          //noinspection BusyWait
          Thread.sleep(50);
        }
        catch (InterruptedException ignored) {
        }
        last = e;
      }
    }
    throw last;
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "ClipboardSynchronizer";
  }

  public void setContent(Transferable content, ClipboardOwner owner) {
    synchronized (myLock) {
      myCurrentContent = content;
    }
    for (int i = 0; i < 3; i++) {
      try {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(content, owner);
      }
      catch (IllegalStateException e) {
        try {
          //noinspection BusyWait
          Thread.sleep(50);
        }
        catch (InterruptedException ignored) {
        }
        continue;
      }
      break;
    }
  }

  public Transferable getContentsSafe() {
    final ID autoReleasePool = Foundation.invoke("NSAutoreleasePool", "new");

    try {
      String plainText = "public.utf8-plain-text";
      String jvmObject = "application/x-java-jvm";

      ID pasteboard = Foundation.invoke("NSPasteboard", "generalPasteboard");
      ID types = Foundation.invoke(pasteboard, "types");
      IntegerType count = Foundation.invoke(types, "count");

      ID plainTextType = null;
      ID vmObjectType = null;

      for (int i = 0; i < count.intValue(); i++) {
        ID each = Foundation.invoke(types, "objectAtIndex:", i);
        String eachType = Foundation.toStringViaUTF8(each);
        if (plainText.equals(eachType)) {
          plainTextType = each;
        }

        if (eachType.contains(jvmObject)) {
          vmObjectType = each;
        }

      }

      if (vmObjectType != null && plainTextType != null) {
        ID text = Foundation.invoke(pasteboard, "stringForType:", plainTextType);
        return new StringSelection(Foundation.toStringViaUTF8(text));
      }
    }
    finally {
      Foundation.invoke(autoReleasePool, Foundation.createSelector("release"));
    }

    return null;
  }
}
