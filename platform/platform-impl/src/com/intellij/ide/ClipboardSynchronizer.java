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
import com.intellij.ide.ui.LafManager;
import com.intellij.ide.ui.LafManagerListener;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.IntegerType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.datatransfer.DataTransferer;

import javax.swing.*;
import javax.swing.text.DefaultEditorKit;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <p>This class is used to workaround the problem with getting clipboard contents (http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4818143).
 * Although this bug is marked as fixed actually Sun just set 10 seconds timeout for {@link java.awt.datatransfer.Clipboard#getContents(Object)}
 * method which may cause unacceptably long UI freezes. So we worked around this as follows:
 * <ul>
 *   <li>for Macs we perform synchronization with system clipboard on a separate thread and schedule it when IDEA frame is activated
 *   or Copy/Cut action in Swing component is invoked, and use native method calls to access system clipboard lock-free (?);</li>
 *   <li>for Linux we temporary set short timeout and check for available formats (which should be fast if a clipboard owner is alive).</li>
 * </ul>
 * </p>
 *
 * @author nik
 */
public class ClipboardSynchronizer implements ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ClipboardSynchronizer");

  @NonNls private static final String ALTERNATIVE_SYNC = "idea.use.alt.clipboard.sync";
  @NonNls private static final String DATA_TRANSFER_TIMEOUT_PROPERTY = "sun.awt.datatransfer.timeout";
  @NonNls private static final String LONG_TIMEOUT = "2000";
  @NonNls private static final String SHORT_TIMEOUT = "100";

  private final ClipboardHandler myClipboardHandler;

  public static ClipboardSynchronizer getInstance() {
    return ApplicationManager.getApplication().getComponent(ClipboardSynchronizer.class);
  }

  public ClipboardSynchronizer() {
    final boolean useAltSync = useAlternativeSync();
    if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS && SystemInfo.isMac) {
      myClipboardHandler = useAltSync ? new MacAltClipboardHandler() : new MacClipboardHandler();
    }
    else if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS && SystemInfo.isLinux) {
      myClipboardHandler = useAltSync ? new LinuxAltClipboardHandler() : new LinuxOldClipboardHandler();
    }
    else {
      myClipboardHandler = new ClipboardHandler();
    }
  }

  public static boolean useAlternativeSync() {
    final String value = System.getProperty(ALTERNATIVE_SYNC);
    return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
  }

  @Override
  public void initComponent() {
    myClipboardHandler.init();
  }

  @Override
  public void disposeComponent() {
    myClipboardHandler.dispose();
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "ClipboardSynchronizer";
  }

  public boolean isDataFlavorAvailable(@NotNull final DataFlavor dataFlavor) {
    try {
      return myClipboardHandler.isDataFlavorAvailable(dataFlavor);
    }
    catch (IllegalStateException e) {
      LOG.info(e);
      return false;
    }
  }

  @Nullable
  public Transferable getContents() {
    try {
      return myClipboardHandler.getContents();
    }
    catch (IllegalStateException e) {
      LOG.info(e);
      return null;
    }
  }

  public void setContent(@NotNull final Transferable content, @NotNull final ClipboardOwner owner) {
    myClipboardHandler.setContent(content, owner);
  }

  public void resetContent() {
    myClipboardHandler.resetContent();
  }

  private static class ClipboardHandler {
    public void init() { }

    public void dispose() { }

    public boolean isDataFlavorAvailable(@NotNull final DataFlavor dataFlavor) {
      return Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(dataFlavor);
    }

    @Nullable
    public Transferable getContents() throws IllegalStateException {
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
          catch (InterruptedException ignored) { }
          last = e;
        }
      }
      throw last;
    }

    public void setContent(@NotNull final Transferable content, @NotNull final ClipboardOwner owner) {
      for (int i = 0; i < 3; i++) {
        try {
          Toolkit.getDefaultToolkit().getSystemClipboard().setContents(content, owner);
        }
        catch (IllegalStateException e) {
          try {
            //noinspection BusyWait
            Thread.sleep(50);
          }
          catch (InterruptedException ignored) { }
          continue;
        }
        break;
      }
    }

    public void resetContent() { }
  }

  private static class MacClipboardHandler extends ClipboardHandler {
    private final AtomicBoolean mySynchronizationInProgress = new AtomicBoolean(false);
    private Transferable myCurrentContent;
    private final Object myContentLock = new Object();
    private final FrameStateListener myFrameStateListener;
    private final LafManagerListener myLafListener;

    public MacClipboardHandler() {
      myFrameStateListener = new FrameStateListener() {
        @Override
        public void onFrameDeactivated() {
        }

        @Override
        public void onFrameActivated() {
          scheduleSynchronization();
        }
      };
      myLafListener = new LafManagerListener() {
        @Override
        public void lookAndFeelChanged(final LafManager source) {
          final UIDefaults uiDefaults = UIManager.getLookAndFeelDefaults();
          replaceDefaultCopyPasteActions(uiDefaults);
        }
      };
    }

    @Override
    public void init() {
      if (System.getProperty(DATA_TRANSFER_TIMEOUT_PROPERTY) == null) {
        System.setProperty(DATA_TRANSFER_TIMEOUT_PROPERTY, LONG_TIMEOUT);
      }

      FrameStateManager.getInstance().addListener(myFrameStateListener);
      LafManager.getInstance().addLafManagerListener(myLafListener);
    }

    @Override
    public void dispose() {
      FrameStateManager.getInstance().removeListener(myFrameStateListener);
      LafManager.getInstance().removeLafManagerListener(myLafListener);
    }

    private void replaceDefaultCopyPasteActions(UIDefaults defaults) {
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
          if (DefaultEditorKit.copyAction.equals(action.getValue(Action.NAME)) ||
              DefaultEditorKit.cutAction.equals(action.getValue(Action.NAME))) {
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
     * @param action action to wrap
     * @return wrapped action
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

    private void scheduleSynchronization() {
      final boolean inProgress = mySynchronizationInProgress.getAndSet(true);
      if (inProgress) return;

      final Application app = ApplicationManager.getApplication();
      app.executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          try {
            final Transferable content = doGetContents();
            synchronized (myContentLock) {
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

    @Nullable
    private Transferable doGetContents() throws IllegalStateException {
      if (SystemInfo.isMac && Registry.is("ide.mac.useNativeClipboard")) {
        final Transferable safe = getContentsSafe();
        if (safe != null) {
          return safe;
        }
      }

      return super.getContents();
    }

    @Override
    public boolean isDataFlavorAvailable(@NotNull final DataFlavor dataFlavor) {
      final Transferable contents = getContents();
      return contents != null && contents.isDataFlavorSupported(dataFlavor);
    }

    @Override
    public Transferable getContents() {
      synchronized (myContentLock) {
        return myCurrentContent;
      }
    }

    @Override
    public void setContent(@NotNull final Transferable content, @NotNull final ClipboardOwner owner) {
      synchronized (myContentLock) {
        myCurrentContent = content;
      }
      super.setContent(content, owner);
    }

    private static Transferable getContentsSafe() {
      final Ref<Transferable> result = new Ref<Transferable>();
      Foundation.executeOnMainThread(new Runnable() {
        @Override
        public void run() {
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
            result.set(new StringSelection(Foundation.toStringViaUTF8(text)));
          }
        }
      }, true, true);

      return result.get();
    }
  }

  private static class LinuxOldClipboardHandler extends MacClipboardHandler { }

  private static class LinuxAltClipboardHandler extends ClipboardHandler {
    private static final FlavorTable FLAVOR_MAP = (FlavorTable)SystemFlavorMap.getDefaultFlavorMap();

    private volatile Transferable myCurrentContent = null;

    @Override
    public void init() {
      if (System.getProperty(DATA_TRANSFER_TIMEOUT_PROPERTY) == null) {
        System.setProperty(DATA_TRANSFER_TIMEOUT_PROPERTY, LONG_TIMEOUT);
      }
    }

    @Override
    public void dispose() {
      resetContent();
    }

    @Override
    public boolean isDataFlavorAvailable(@NotNull final DataFlavor dataFlavor) {
      final Transferable currentContent = myCurrentContent;
      if (currentContent != null) {
        return currentContent.isDataFlavorSupported(dataFlavor);
      }

      final Collection<DataFlavor> flavors = checkContentsQuick();
      if (flavors != null) {
        return flavors.contains(dataFlavor);
      }

      return super.isDataFlavorAvailable(dataFlavor);
    }

    @Override
    public Transferable getContents() throws IllegalStateException {
      final Transferable currentContent = myCurrentContent;
      if (currentContent != null) {
        return currentContent;
      }

      final Collection<DataFlavor> flavors = checkContentsQuick();
      if (flavors != null && flavors.isEmpty()) {
        return null;
      }

      return super.getContents();
    }

    @Override
    public void setContent(@NotNull final Transferable content, @NotNull final ClipboardOwner owner) {
      myCurrentContent = content;
      super.setContent(content, owner);
    }

    @Override
    public void resetContent() {
      myCurrentContent = null;
    }

    /**
     * Quickly checks availability of data in X11 clipboard selection.
     *
     * @return null if is unable to check; empty list if clipboard owner doesn't respond timely;
     *         collection of available data flavors otherwise.
     */
    @Nullable
    private static Collection<DataFlavor> checkContentsQuick() {
      final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      final Class<? extends Clipboard> aClass = clipboard.getClass();
      if (!"sun.awt.X11.XClipboard".equals(aClass.getName())) return null;

      final Method getClipboardFormats;
      try {
        getClipboardFormats = aClass.getDeclaredMethod("getClipboardFormats");
        getClipboardFormats.setAccessible(true);
      }
      catch (Exception ignore) {
        return null;
      }

      final String timeout = System.getProperty(DATA_TRANSFER_TIMEOUT_PROPERTY);
      System.setProperty(DATA_TRANSFER_TIMEOUT_PROPERTY, SHORT_TIMEOUT);

      try {
        final long[] formats = (long[])getClipboardFormats.invoke(clipboard);
        if (formats == null || formats.length == 0) {
          return Collections.emptySet();
        }
        else {
          //noinspection unchecked
          return DataTransferer.getInstance().getFlavorsForFormats(formats, FLAVOR_MAP).keySet();
        }
      }
      catch (IllegalAccessException ignore) { }
      catch (IllegalArgumentException ignore) { }
      catch (InvocationTargetException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof IllegalStateException) {
          throw (IllegalStateException)cause;
        }
      }
      finally {
        System.setProperty(DATA_TRANSFER_TIMEOUT_PROPERTY, timeout);
      }

      return null;
    }
  }

  private static class MacAltClipboardHandler extends ClipboardHandler {
    private Pair<String,Transferable> myFullTransferable;

    @Nullable
    private Transferable doGetContents() throws IllegalStateException {
      if (Registry.is("ide.mac.useNativeClipboard")) {
        final Transferable safe = getContentsSafe();
        if (safe != null) {
          return safe;
        }
      }

      return super.getContents();
    }

    @Override
    public boolean isDataFlavorAvailable(@NotNull final DataFlavor dataFlavor) {
      final Transferable contents = getContents();
      return contents != null && contents.isDataFlavorSupported(dataFlavor);
    }

    @Override
    public Transferable getContents() {
      Transferable transferable = doGetContents();
      if (transferable != null && myFullTransferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        try {
          String stringData = (String) transferable.getTransferData(DataFlavor.stringFlavor);
          if (stringData != null && stringData.equals(myFullTransferable.getFirst())) {
            return myFullTransferable.getSecond();
          }
        }
        catch (UnsupportedFlavorException e) {
          LOG.info(e);
        }
        catch (IOException e) {
          LOG.info(e);
        }
      }

      myFullTransferable = null;
      return transferable;
    }

    @Override
    public void resetContent() {
      //myFullTransferable = null;
      super.resetContent();
    }

    @Override
    public void setContent(@NotNull final Transferable content, @NotNull final ClipboardOwner owner) {
      if (Registry.is("ide.mac.useNativeClipboard") && content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        try {
          String stringData = (String) content.getTransferData(DataFlavor.stringFlavor);
          myFullTransferable = Pair.create(stringData, content);
          super.setContent(new StringSelection(stringData), owner);
        }
        catch (UnsupportedFlavorException e) {
          LOG.info(e);
        }
        catch (IOException e) {
          LOG.info(e);
        }
      } else {
        myFullTransferable = null;
        super.setContent(content, owner);
      }
    }

    public static Transferable getContentsSafe() {
      final Ref<Transferable> result = new Ref<Transferable>();
      Foundation.executeOnMainThread(new Runnable() {
        @Override
        public void run() {
          String plainText = "public.utf8-plain-text";
          String jvmObject = "application/x-java-jvm";

          ID pasteboard = Foundation.invoke("NSPasteboard", "generalPasteboard");
          ID types = Foundation.invoke(pasteboard, "types");
          IntegerType count = Foundation.invoke(types, "count");

          ID plainTextType = null;

          for (int i = 0; i < count.intValue(); i++) {
            ID each = Foundation.invoke(types, "objectAtIndex:", i);
            String eachType = Foundation.toStringViaUTF8(each);
            if (plainText.equals(eachType)) {
              plainTextType = each;
              break;
            }
          }

          // will put string value even if we doesn't found java object. this is needed because java caches clipboard value internally and
          // will reset it ONLY IF we'll put jvm-object into clipboard (see our setContent optimizations which avoids putting jvm-objects
          // into clipboard)

          if (plainTextType != null) {
            ID text = Foundation.invoke(pasteboard, "stringForType:", plainTextType);
            String value = Foundation.toStringViaUTF8(text);
            if (value == null) {
              LOG.info(String.format("[Clipboard] Strange string value (null?) for type: %s", plainTextType));
            }
            else {
              result.set(new StringSelection(value));
            }
          }
        }
      }, true, true);

      return result.get();
    }
  }
}
