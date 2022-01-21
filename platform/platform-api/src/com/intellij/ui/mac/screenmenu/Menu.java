// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.screenmenu;

import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
public class Menu extends MenuItem {
  private static final boolean USE_STUB = Boolean.getBoolean("jbScreenMenuBar.useStubItem"); // just for tests/experiments
  private static final int CLOSE_DELAY = Integer.getInteger("jbScreenMenuBar.closeDelay", 500); // in milliseconds
  private static Boolean IS_ENABLED = null;
  private final List<MenuItem> myItems = new ArrayList<>();
  private final List<MenuItem> myBuffer = new ArrayList<>();
  private Runnable myOnOpen;
  private Runnable myOnClose; // we assume that can run it only on EDT (to change swing components)
  private Component myComponent;

  long[] myCachedPeers;

  public Menu(String title) {
    setTitle(title);
  }

  public void setOnOpen(Runnable fillMenuProcedure, Component component) {
    this.myOnOpen = fillMenuProcedure;
    this.myComponent = component;
  }

  public void setOnClose(Runnable onClose, Component component) {
    this.myOnClose = onClose;
    this.myComponent = component;
  }

  @Override
  public void propertyChange(PropertyChangeEvent e) {
    @NonNls String propertyName = e.getPropertyName();
    if (Presentation.PROP_TEXT.equals(propertyName) || Presentation.PROP_DESCRIPTION.equals(propertyName)) {
      setTitle(presentation.getText());
    }
  }

  public void setTitle(String label) {
    ensureNativePeer();
    nativeSetTitle(nativePeer, label, isInHierarchy);
  }

  @SuppressWarnings("SSBasedInspection")
  void disposeChildren(int delayMs) {
    if (delayMs <= 0) {
      for (MenuItem item : myItems)
        item.dispose();
    } else {
      // Schedule to dispose later.
      // NOTE: Can be invoked in any thread, not only EDT
      final ArrayList<MenuItem> copy = new ArrayList<>(myItems);
      SimpleTimer.getInstance().setUp(() -> {
        for (MenuItem item: copy)
          item.dispose();
      }, delayMs);
    }
    myItems.clear();
  }

  @Override
  synchronized
  public void dispose() {
    disposeChildren(0);
    myCachedPeers = null;
    super.dispose();
  }

  public void beginFill() {
    for (MenuItem item : myBuffer)
      if (item != null) item.dispose();
    myBuffer.clear();
  }

  public @Nullable MenuItem add(@Nullable MenuItem item) { myBuffer.add(item); return item; }

  synchronized
  public void endFill(boolean onAppKit) {
    disposeChildren(0); // NOTE: to test/increase stability use 2000 ms

    if (myBuffer.isEmpty()) return;

    myCachedPeers = new long[myBuffer.size()];
    //System.err.println("refill with " + newItemsPeers.length + " items");
    for (int c = 0; c < myBuffer.size(); ++c) {
      MenuItem menuItem = myBuffer.get(c);
      if (menuItem != null) {
        menuItem.ensureNativePeer();
        myCachedPeers[c] = menuItem.nativePeer;
        //System.err.printf("\t0x%X\n", newItemsPeers[c]);
        myItems.add(menuItem);
        menuItem.isInHierarchy = true;
      } else {
        myCachedPeers[c] = 0;
      }
    }
    myBuffer.clear();

    refillImpl(onAppKit);
  }

  synchronized void refillImpl(boolean onAppKit) {
    ensureNativePeer();
    if (myCachedPeers != null)
      nativeRefill(nativePeer, myCachedPeers, onAppKit);
  }

  synchronized
  public void endFill() {
    endFill(true);
  }

  @Override
  synchronized
  void ensureNativePeer() {
    if (nativePeer == 0) {
      nativePeer = nativeCreateMenu();
    }
  }

  public void invokeOpenLater() {
    // Called on AppKit when menu opening
    if (myOnOpen != null) {
      if (USE_STUB) {
        // NOTE: must add stub item when menu opens (otherwise AppKit considers it as empty and we can't fill it later)
        MenuItem stub = new MenuItem();
        myItems.add(stub);
        stub.isInHierarchy = true;

        ensureNativePeer();
        stub.ensureNativePeer();
        nativeAddItem(nativePeer, stub.nativePeer, false/*already on AppKit thread*/);

        ApplicationManager.getApplication().invokeLater(()->{
          myOnOpen.run();
          endFill(true);
        });
      } else {
        invokeWithLWCToolkit(myOnOpen, ()->endFill(false/*already on AppKit thread*/), myComponent);
      }
    }
  }

  public void invokeMenuClosing() {
    // Called on AppKit when menu closed

    // When user selects item of system menu (under macOS) AppKit sometimes generates such sequence: CloseParentMenu -> PerformItemAction
    // So we can destroy menu-item before item's action performed, and because of that action will not be executed.
    // Defer clearing to avoid this problem.
    disposeChildren(CLOSE_DELAY);
    SimpleTimer.getInstance().setUp(() -> {
      synchronized (this) {
        // clean native NSMenu item
        if (nativePeer != 0) nativeRefill(nativePeer, null, true);
      }
    }, CLOSE_DELAY);
    if (myOnClose != null) invokeWithLWCToolkit(myOnClose, null, myComponent);
  }

  //
  // Native methods
  //

  // Creates native peer (wrapper for NSMenu with corresponding NSMenuItem).
  // User must dealloc it via nativeDispose after usage.
  // Can be invoked from any thread.
  private native long nativeCreateMenu();

  // If menu was created but wasn't added into any parent menu then all setters can be invoked from any thread.
  private native void nativeSetTitle(long menuPtr, String title, boolean onAppKit);
  private native void nativeAddItem(long menuPtr, long itemPtr/*MenuItem OR Menu*/, boolean onAppKit);

  // Refill menu.
  // If menuPtr == null then refills sharedApplication.mainMenu with new items (except AppMenu, i.e. first item of mainMenu)
  native void nativeRefill(long menuPtr, long[] newItems, boolean onAppKit);

  static
  private native void nativeInitClass();

  //
  // Static API
  //

  public static boolean isJbScreenMenuEnabled() {
    if (IS_ENABLED != null)
      return IS_ENABLED;

    IS_ENABLED = false;

    if (!SystemInfo.isMac) return false;
    if (!Boolean.getBoolean("jbScreenMenuBar.enabled")) return false;
    if (Boolean.getBoolean("apple.laf.useScreenMenuBar")) {
      Logger.getInstance(Menu.class).info("apple.laf.useScreenMenuBar==true, default screen menu implementation will be used");
      return false;
    }

    Path lib = PathManager.findBinFile("libmacscreenmenu64.dylib");
    try {
      System.load(lib.toFile().getAbsolutePath());
      nativeInitClass();
      // create and dispose native object (just for to test)
      Menu test = new Menu("test");
      test.ensureNativePeer();
      test.dispose();
      IS_ENABLED = true;
      Logger.getInstance(Menu.class).info("use new ScreenMenuBar implementation");
    } catch (Throwable e) {
      // default screen menu implementation will be used
      Logger.getInstance(Menu.class).warn("can't load menu library: " + lib.toFile().getAbsolutePath() + ", exception: " + e.getMessage());
    }

    return IS_ENABLED;
  }

  private static void invokeWithLWCToolkit(Runnable r, Runnable after, Component invoker) {
    try {
      Class toolkitClass = Class.forName("sun.lwawt.macosx.LWCToolkit");
      Method invokeMethod = ReflectionUtil.getDeclaredMethod(toolkitClass, "invokeAndWait", Runnable.class, Component.class);
      if (invokeMethod != null) {
        try {
          invokeMethod.invoke(toolkitClass, r, invoker);
        } catch (Exception e) {
          // suppress InvocationTargetException as in openjdk implementation (see com.apple.laf.ScreenMenu.java)
          Logger.getInstance(Menu.class).debug("invokeWithLWCToolkit.invokeAndWait: " + e);
        }
        if (after != null) after.run();
      } else {
        Logger.getInstance(Menu.class).warn("can't find sun.lwawt.macosx.LWCToolkit.invokeAndWait, screen menu won't be filled");
      }
    } catch (ClassNotFoundException e) {
      Logger.getInstance(Menu.class).warn("can't find sun.lwawt.macosx.LWCToolkit, screen menu won't be filled");
    }
  }
}
