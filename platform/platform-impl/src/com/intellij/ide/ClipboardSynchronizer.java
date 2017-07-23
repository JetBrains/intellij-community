/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.ClipboardUtil;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.Consumer;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.concurrency.FutureResult;
import com.sun.jna.IntegerType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sun.awt.datatransfer.DataTransferer;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * This class is used to workaround the problem with getting clipboard contents (http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4818143).
 * Although this bug is marked as fixed actually Sun just set 10 seconds timeout for {@link Clipboard#getContents(Object)}
 * method which may cause unacceptably long UI freezes. So we worked around this as follows:
 * <ul>
 * <li>for Macs we perform synchronization with system clipboard on a separate thread and schedule it when IDEA frame is activated
 * or Copy/Cut action in Swing component is invoked, and use native method calls to access system clipboard lock-free (?);</li>
 * <li>for X Window we temporary set short timeout and check for available formats (which should be fast if a clipboard owner is alive).</li>
 * </ul>
 *
 * @author nik
 */
public class ClipboardSynchronizer implements Disposable, ApplicationComponent {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ClipboardSynchronizer");

  private final ClipboardHandler myClipboardHandler;

  public static ClipboardSynchronizer getInstance() {
    return ApplicationManager.getApplication().getComponent(ClipboardSynchronizer.class);
  }

  public ClipboardSynchronizer() {
    if (ApplicationManager.getApplication().isHeadlessEnvironment() && ApplicationManager.getApplication().isUnitTestMode()) {
      myClipboardHandler = new HeadlessClipboardHandler();
    }
    else if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS && SystemInfo.isMac) {
      myClipboardHandler = new MacClipboardHandler();
    }
    else if (Patches.SLOW_GETTING_CLIPBOARD_CONTENTS && SystemInfo.isXWindow) {
      myClipboardHandler = new XWinClipboardHandler();
    }
    else {
      myClipboardHandler = new ClipboardHandler();
    }
  }

  @Override
  public void initComponent() {
    myClipboardHandler.init();
  }

  @Override
  public void dispose() {
    myClipboardHandler.dispose();
  }

  public void areDataFlavorsAvailableAsync(@NotNull Consumer<Boolean> callback, @NotNull DataFlavor... flavors) {
    final Supplier<Boolean> availabilitySupplier =
      () -> ClipboardUtil.handleClipboardSafely(() -> myClipboardHandler.areDataFlavorsAvailable(flavors),() -> false);

    Boolean available = availabilitySupplier.get();
    if (available) {
      callback.consume(available);
    } else {
      AtomicInteger counter = new AtomicInteger();

      Timer timer = new Timer(50, event -> {});
      timer.addActionListener( event -> {
        Boolean a = availabilitySupplier.get();
        if (counter.incrementAndGet() > 3 || a) {
          timer.stop();
        }
        callback.consume(a);
      });
      timer.start();
    }
  }

  public void getContentsAsync(@NotNull Consumer<Transferable> callback) {
    final Supplier<Transferable> transferableSupplier =
      () -> ClipboardUtil.handleClipboardSafely(myClipboardHandler::getContents, () -> null);

    Transferable transferable = transferableSupplier.get();
    if (transferable != null) {
      callback.consume(transferable);
    } else {
      AtomicInteger counter = new AtomicInteger();

      Timer timer = new Timer(50, event -> {});
      timer.addActionListener( event -> {
        Transferable t = transferableSupplier.get();
        if (counter.incrementAndGet() > 3) {
          timer.stop();
        }
        callback.consume(t);
      });
      timer.start();
    }
  }

  public boolean areDataFlavorsAvailable(@NotNull DataFlavor... flavors) {
    return ClipboardUtil.handleClipboardSafely(() -> myClipboardHandler.areDataFlavorsAvailable(flavors), () -> false);
  }

  @Nullable
  public Transferable getContents() {
    return ClipboardUtil.handleClipboardSafely(myClipboardHandler::getContents, () -> null);
  }

  @Nullable
  public Object getData(@NotNull DataFlavor dataFlavor) {
    return ClipboardUtil.handleClipboardSafely(() -> {
      try {
        return myClipboardHandler.getData(dataFlavor);
      }
      catch (IOException | UnsupportedFlavorException e) {
        LOG.debug(e);
        return null;
      }
    }, () -> null);
  }

  public void setContent(@NotNull final Transferable content, @NotNull final ClipboardOwner owner) {
    myClipboardHandler.setContent(content, owner);
  }

  public void resetContent() {
    myClipboardHandler.resetContent();
  }

  @Nullable
  private static Clipboard getClipboard() {
    try {
      return Toolkit.getDefaultToolkit().getSystemClipboard();
    }
    catch (IllegalStateException e) {
      if (SystemInfo.isWindows) {
        LOG.debug("Clipboard is busy");
      } else {
        LOG.warn(e);
      }
      return null;
    }
  }

  private static class ClipboardHandler {
    public void init() { }

    public void dispose() { }

    public boolean areDataFlavorsAvailable(@NotNull DataFlavor... flavors) {
      Clipboard clipboard = getClipboard();
      if (clipboard == null) return false;
      for (DataFlavor flavor : flavors) {
        if (clipboard.isDataFlavorAvailable(flavor)) {
          return true;
        }
      }
      return false;
    }


    @Nullable
    public Transferable getContents() {
      Clipboard clipboard = getClipboard();
      return clipboard == null ? null: clipboard.getContents(this);
    }

    @Nullable
    public Object getData(@NotNull DataFlavor dataFlavor) throws IOException, UnsupportedFlavorException {
      Clipboard clipboard = getClipboard();
      return clipboard == null ? null : clipboard.getData(dataFlavor);
    }

    public void setContent(@NotNull final Transferable content, @NotNull final ClipboardOwner owner) {
      Clipboard clipboard = getClipboard();
      if (clipboard !=null) {
        clipboard.setContents(content, owner);
      }
    }

    public void resetContent() {
    }
  }


  private static class MacClipboardHandler extends ClipboardHandler {
    private Pair<String,Transferable> myFullTransferable;

    @Nullable
    private Transferable doGetContents() {
      return super.getContents();
    }

    @Override
    public boolean areDataFlavorsAvailable(@NotNull DataFlavor... flavors) {
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
          if (stringData != null && stringData.equals(myFullTransferable.getFirst())) {
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
    @Nullable
    public Object getData(@NotNull DataFlavor dataFlavor) throws IOException, UnsupportedFlavorException {
      if (myFullTransferable == null) return super.getData(dataFlavor);
      Transferable contents = getContents();
      return contents == null ? null : contents.getTransferData(dataFlavor);
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
        catch (UnsupportedFlavorException | IOException e) {
          LOG.info(e);
        }
      } else {
        myFullTransferable = null;
        super.setContent(content, owner);
      }
    }

    @Nullable
    private static Transferable getContentsSafe() {
      final FutureResult<Transferable> result = new FutureResult<>();

      Foundation.executeOnMainThread(true, false, () -> {
        Transferable transferable = getClipboardContentNatively();
        if (transferable != null) {
          result.set(transferable);
        }
      });

      try {
        return result.get(10, TimeUnit.MILLISECONDS);
      }
      catch (Exception ignored) {
        return null;
      }
    }

    @Nullable
    private static Transferable getClipboardContentNatively() {
      String plainText = "public.utf8-plain-text";

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

      Transferable result = null;
      if (plainTextType != null) {
        ID text = Foundation.invoke(pasteboard, "stringForType:", plainTextType);
        String value = Foundation.toStringViaUTF8(text);
        if (value == null) {
          LOG.info(String.format("[Clipboard] Strange string value (null?) for type: %s", plainTextType));
        }
        else {
          result = new StringSelection(value);
        }
      }

      return result;
    }
  }


  private static class XWinClipboardHandler extends ClipboardHandler {
    private static final String DATA_TRANSFER_TIMEOUT_PROPERTY = "sun.awt.datatransfer.timeout";
    private static final String LONG_TIMEOUT = "2000";
    private static final String SHORT_TIMEOUT = "100";
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
    public boolean areDataFlavorsAvailable(@NotNull DataFlavor... flavors) {
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
    @Nullable
    public Object getData(@NotNull DataFlavor dataFlavor) throws IOException, UnsupportedFlavorException {
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
        @SuppressWarnings({"unchecked"}) final Set<DataFlavor> set = DataTransferer.getInstance().getFlavorsForFormats(formats, FLAVOR_MAP).keySet();
        return set;
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


  private static class HeadlessClipboardHandler extends ClipboardHandler {
    private volatile Transferable myContent = null;

    @Override
    public boolean areDataFlavorsAvailable(@NotNull DataFlavor... flavors) {
      Transferable content = myContent;
      return content != null && ClipboardSynchronizer.areDataFlavorsAvailable(content, flavors);
    }

    @Override
    public Transferable getContents() throws IllegalStateException {
      return myContent;
    }

    @Override
    @Nullable
    public Object getData(@NotNull DataFlavor dataFlavor) throws IOException, UnsupportedFlavorException {
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
}