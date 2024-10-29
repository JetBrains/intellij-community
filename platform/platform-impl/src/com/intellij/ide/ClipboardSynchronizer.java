// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.Patches;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ClipboardUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.UIBundle;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.StartupUiUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.datatransfer.DataTransferer;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;

/**
 * This class is used to workaround <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4818143">the problem</a> with getting clipboard contents.
 * Although this bug is marked as fixed actually Sun just set 10 seconds timeout for {@link Clipboard#getContents(Object)}
 * method which may cause unacceptably long UI freezes. So we worked around this as follows:
 * <ul>
 * <li>for Macs we perform synchronization with system clipboard on a separate thread and schedule it when IDEA frame is activated
 * or Copy/Cut action in Swing component is invoked, and use native method calls to access system clipboard lock-free (?);</li>
 * <li>for X Window we temporary set short timeout and check for available formats (which should be fast if a clipboard owner is alive).</li>
 * </ul>
 */
public final class ClipboardSynchronizer implements Disposable {
  private static final Logger LOG = Logger.getInstance(ClipboardSynchronizer.class);

  private final ClipboardHandler myClipboardHandler;

  public static ClipboardSynchronizer getInstance() {
    return ApplicationManager.getApplication().getService(ClipboardSynchronizer.class);
  }

  public ClipboardSynchronizer() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment()) {
      myClipboardHandler = new HeadlessClipboardHandler();
    }
    else if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS && SystemInfoRt.isMac) {
      myClipboardHandler = new MacClipboardHandler();
    }
    else if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS && StartupUiUtil.isXToolkit()) {
      myClipboardHandler = new XWinClipboardHandler();
    }
    else if (SystemInfoRt.isWindows) {
      myClipboardHandler = new WindowsClipboardHandler();
    }
    else {
      myClipboardHandler = new ClipboardHandler();
    }

    myClipboardHandler.init();
  }

  @Override
  public void dispose() {
    myClipboardHandler.dispose();
  }

  public boolean areDataFlavorsAvailable(DataFlavor @NotNull ... flavors) {
    return ClipboardUtil.handleClipboardSafely(() -> myClipboardHandler.areDataFlavorsAvailable(flavors), false);
  }

  public @Nullable Transferable getContents() {
    return ClipboardUtil.handleClipboardSafely(myClipboardHandler::getContents, null);
  }

  public @Nullable Object getData(@NotNull DataFlavor dataFlavor) {
    return ClipboardUtil.handleClipboardSafely(() -> {
      try {
        return myClipboardHandler.getData(dataFlavor);
      }
      catch (IOException | UnsupportedFlavorException e) {
        LOG.debug(e);
        return null;
      }
    }, null);
  }

  public void setContent(final @NotNull Transferable content, final @NotNull ClipboardOwner owner) {
    myClipboardHandler.setContent(content, owner);
  }

  public void resetContent() {
    myClipboardHandler.resetContent();
  }

  private static @Nullable Clipboard getClipboard() {
    try {
      //noinspection SSBasedInspection: this is low-level clipboard infra, allowed to access the real clipboard
      return Toolkit.getDefaultToolkit().getSystemClipboard();
    }
    catch (IllegalStateException e) {
      if (SystemInfoRt.isWindows) {
        LOG.debug("Clipboard is busy");
      }
      else {
        LOG.warn(e);
      }
      return null;
    }
  }

  private static class ClipboardHandler {
    public void init() { }

    public void dispose() { }

    @FunctionalInterface
    protected interface MultiThrowingSupplier<R, E1 extends Throwable, E2 extends Throwable> {
      R get() throws E1, E2;
    }
    protected <R, E1 extends Throwable, E2 extends Throwable> R wrapClipboardAccess(MultiThrowingSupplier<R, E1, E2> function) throws E1, E2 {
      return function.get();
    }

    protected void wrapClipboardAccess(Runnable function) {
      function.run();
    }

    public boolean areDataFlavorsAvailable(DataFlavor @NotNull ... flavors) {
      Clipboard clipboard = getClipboard();
      if (clipboard == null) return false;
      for (DataFlavor flavor : flavors) {
        if (wrapClipboardAccess(() -> clipboard.isDataFlavorAvailable(flavor))) {
          return true;
        }
      }
      return false;
    }

    public @Nullable Transferable getContents() {
      Clipboard clipboard = getClipboard();
      if (clipboard == null) {
        return null;
      }
      Transferable contents = wrapClipboardAccess(() -> clipboard.getContents(this));
      if (LOG.isDebugEnabled()) {
        // this temporary logging is needed to investigate clipboard pasting issue (see IDEA-316996)
        LOG.debug("Clipboard class: " + clipboard.getClass().getName());
        LOG.debug("ClipboardHandler class: " + getClass().getName());
        try {
          String text = (String) contents.getTransferData(DataFlavor.stringFlavor);
          text = text.substring(0, Math.min(text.length(), 256)).replaceAll("\\R", " ");
          LOG.debug("Transferable contents: " + text);
        } catch (Exception e) {
          LOG.debug(e);
        }
      }
      return contents;
    }

    public @Nullable Object getData(@NotNull DataFlavor dataFlavor) throws IOException, UnsupportedFlavorException {
      Clipboard clipboard = getClipboard();
      return clipboard == null
             ? null :
             this.<Object, IOException, UnsupportedFlavorException>wrapClipboardAccess(() -> clipboard.getData(dataFlavor));
    }

    public void setContent(@NotNull Transferable content, final @NotNull ClipboardOwner owner) {
      Clipboard clipboard = getClipboard();
      if (clipboard == null) {
        return;
      }

      IllegalStateException lastException = null;
      for (int i = 0; i < getRetries(); i++) {
        try {
          wrapClipboardAccess(() -> clipboard.setContents(content, owner));
          return;
        }
        catch (IllegalStateException e) {
          lastException = e;
        }
      }
      LOG.debug(lastException);
      NotificationGroupManager.getInstance().getNotificationGroup("System Clipboard")
        .createNotification(UIBundle.message("clipboard.is.unavailable"), MessageType.WARNING)
        .notify(null);
    }

    public void resetContent() {
    }

    protected int getRetries() {
      return 1;
    }
  }

  private static final class MacClipboardHandler extends ClipboardHandler {
    private Pair<String, Transferable> myFullTransferable;

    private @Nullable Transferable doGetContents() {
      return super.getContents();
    }

    @Override
    public boolean areDataFlavorsAvailable(DataFlavor @NotNull ... flavors) {
      if (myFullTransferable == null) return super.areDataFlavorsAvailable(flavors);
      Transferable contents = getContents();
      return contents != null && ClipboardSynchronizer.areDataFlavorsAvailable(contents, flavors);
    }

    @Override
    public Transferable getContents() {
      Transferable transferable = doGetContents();
      if (transferable != null && myFullTransferable != null && transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        try {
          String stringData = (String) transferable.getTransferData(DataFlavor.stringFlavor);
          if (stringData.equals(myFullTransferable.getFirst())) {
            return myFullTransferable.getSecond();
          }
        }
        catch (UnsupportedFlavorException | IOException e) {
          LOG.info(e);
        }
      }

      myFullTransferable = null;
      return transferable;
    }

    @Override
    public @Nullable Object getData(@NotNull DataFlavor dataFlavor) throws IOException, UnsupportedFlavorException {
      if (myFullTransferable == null) return super.getData(dataFlavor);
      Transferable contents = getContents();
      return contents == null ? null : contents.getTransferData(dataFlavor);
    }

    @Override
    public void setContent(final @NotNull Transferable content, final @NotNull ClipboardOwner owner) {
      if (Registry.is("ide.mac.useNativeClipboard") && content.isDataFlavorSupported(DataFlavor.stringFlavor)) {
        try {
          String stringData = (String) content.getTransferData(DataFlavor.stringFlavor);
          myFullTransferable = Pair.create(stringData, content);
          super.setContent(new StringSelection(stringData), owner);
        }
        catch (UnsupportedFlavorException | IOException e) {
          LOG.info(e);
        }
      }
      else {
        myFullTransferable = null;
        super.setContent(content, owner);
      }
    }
  }

  private static final class XWinClipboardHandler extends ClipboardHandler {
    private static final String DATA_TRANSFER_TIMEOUT_PROPERTY = "sun.awt.datatransfer.timeout";
    private static final String LONG_TIMEOUT = "2000";
    private static final String SHORT_TIMEOUT = "300";
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
    public boolean areDataFlavorsAvailable(DataFlavor @NotNull ... flavors) {
      Transferable currentContent = myCurrentContent;
      if (currentContent != null) {
        return ClipboardSynchronizer.areDataFlavorsAvailable(currentContent, flavors);
      }

      Collection<DataFlavor> contents = checkContentsQuick();
      if (contents != null) {
        return ClipboardSynchronizer.areDataFlavorsAvailable(contents, flavors);
      }

      return super.areDataFlavorsAvailable(flavors);
    }

    @Override
    public Transferable getContents() throws IllegalStateException {
      final Transferable currentContent = myCurrentContent;
      if (currentContent != null) {
        return currentContent;
      }

      Collection<DataFlavor> contents = checkContentsQuick();
      if (contents != null && contents.isEmpty()) {
        return null;
      }

      return super.getContents();
    }

    @Override
    public @Nullable Object getData(@NotNull DataFlavor dataFlavor) throws IOException, UnsupportedFlavorException {
      Transferable currentContent = myCurrentContent;
      if (currentContent != null) {
        return currentContent.getTransferData(dataFlavor);
      }

      Collection<DataFlavor> contents = checkContentsQuick();
      if (contents != null && !contents.contains(dataFlavor)) {
        return null;
      }

      return super.getData(dataFlavor);
    }

    @Override
    public void setContent(final @NotNull Transferable content, final @NotNull ClipboardOwner owner) {
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
    private static @Nullable Collection<DataFlavor> checkContentsQuick() {
      final Clipboard clipboard = getClipboard();
      if (clipboard == null) return null;
      final Class<? extends Clipboard> aClass = clipboard.getClass();
      if (!"sun.awt.X11.XClipboard".equals(aClass.getName())) return null;

      final Method getClipboardFormats = ReflectionUtil.getDeclaredMethod(aClass, "getClipboardFormats");
      if (getClipboardFormats == null) return null;

      final String timeout = System.getProperty(DATA_TRANSFER_TIMEOUT_PROPERTY);
      System.setProperty(DATA_TRANSFER_TIMEOUT_PROPERTY, SHORT_TIMEOUT);

      try {
        final long[] formats = (long[])getClipboardFormats.invoke(clipboard);
        if (formats == null || formats.length == 0) {
          return Collections.emptySet();
        }
        return DataTransferer.getInstance().getFlavorsForFormats(formats, FLAVOR_MAP).keySet();
      }
      catch (IllegalAccessException | IllegalArgumentException ignore) { }
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

  private static final class HeadlessClipboardHandler extends ClipboardHandler {
    private volatile Transferable myContent = null;

    @Override
    public boolean areDataFlavorsAvailable(DataFlavor @NotNull ... flavors) {
      Transferable content = myContent;
      return content != null && ClipboardSynchronizer.areDataFlavorsAvailable(content, flavors);
    }

    @Override
    public Transferable getContents() throws IllegalStateException {
      return myContent;
    }

    @Override
    public @NotNull Object getData(@NotNull DataFlavor dataFlavor) throws IOException, UnsupportedFlavorException {
      return myContent.getTransferData(dataFlavor);
    }

    @Override
    public void setContent(@NotNull Transferable content, @NotNull ClipboardOwner owner) {
      myContent = content;
    }

    @Override
    public void resetContent() {
      myContent = null;
    }
  }

  private static boolean areDataFlavorsAvailable(Transferable contents, DataFlavor... flavors) {
    for (DataFlavor flavor : flavors) {
      if (contents.isDataFlavorSupported(flavor)) {
        return true;
      }
    }
    return false;
  }

  private static boolean areDataFlavorsAvailable(Collection<DataFlavor> contents, DataFlavor... flavors) {
    for (DataFlavor flavor : flavors) {
      if (contents.contains(flavor)) {
        return true;
      }
    }
    return false;
  }

  private static final class WindowsClipboardHandler extends ClipboardHandler {

    // Workaround for IJPL-163459: concurrent access to clipboard triggers crashes in JDK on Windows.
    private final Object syncRoot = new Object();

    @Override
    protected <R, E1 extends Throwable, E2 extends Throwable> R wrapClipboardAccess(MultiThrowingSupplier<R, E1, E2> function) throws E1, E2 {
      synchronized (syncRoot) {
        return super.wrapClipboardAccess(function);
      }
    }

    @Override
    protected void wrapClipboardAccess(Runnable function) {
      synchronized (syncRoot) {
        super.wrapClipboardAccess(function);
      }
    }

    @Override
    protected int getRetries() {
      // Clipboard#setContents throws IllegalStateException if the clipboard is currently unavailable.
      // On Windows, it uses Win32 OpenClipboard which may fail according to its documentation:
      //   "OpenClipboard fails if another window has the clipboard open."
      // Other applications implement retry logic when calling OpenClipboard. Let's do the same.
      //
      // According to my simple local stress testing, Clipboard#setContents hasn't failed more than 2 times in a row.
      // Probably, it needs to be adjusted in future.
      return 5;
    }
  }
}