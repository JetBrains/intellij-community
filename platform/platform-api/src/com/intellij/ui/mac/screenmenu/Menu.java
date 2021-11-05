// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.screenmenu;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
public class Menu extends MenuItem {
  private static final boolean USE_STUB = Boolean.getBoolean("jbScreenMenuBar.useStubItem"); // just for tests/experiments
  private static Boolean IS_ENABLED = null;
  private static final List<MenuItem> ourRootItems = new ArrayList<>();
  private final List<MenuItem> myItems = new ArrayList<>();
  private final List<MenuItem> myBuffer = new ArrayList<>();
  private Runnable myOnClose; // we assume that can run it only on EDT (to change swing components)
  private Component myComponent;

  public Menu(String title) {
    setTitle(title);
  }

  public void setOnOpen(Runnable fillMenuProcedure, Component component) {
    this.actionDelegate = fillMenuProcedure;
    this.myComponent = component;
  }

  public void setOnClose(Runnable onClose, Component component) {
    this.myOnClose = onClose;
    this.myComponent = component;
  }

  public void setTitle(String label) {
    ensureNativePeer();
    nativeSetTitle(nativePeer,label);
  }

  // If item was created but wasn't added into any parent menu then can be invoked from any thread.
  public void addItem(@NotNull MenuItem menuItem, boolean onAppKit) {
    myItems.add(menuItem);

    ensureNativePeer();
    menuItem.ensureNativePeer();
    nativeAddItem(nativePeer, menuItem.nativePeer, onAppKit);
  }

  @SuppressWarnings("SSBasedInspection")
  private void disposeChildren(int delayMs) {
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
    if (nativePeer != 0) {
      nativeDisposeMenu(nativePeer);
      nativePeer = 0;
    }
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

    long[] newItemsPeers = new long[myBuffer.size()];
    //System.err.println("refill with " + newItemsPeers.length + " items");
    for (int c = 0; c < myBuffer.size(); ++c) {
      MenuItem menuItem = myBuffer.get(c);
      if (menuItem != null) {
        menuItem.ensureNativePeer();
        newItemsPeers[c] = menuItem.nativePeer;
        //System.err.printf("\t0x%X\n", newItemsPeers[c]);
        myItems.add(menuItem);
      } else {
        newItemsPeers[c] = 0;
      }
    }
    myBuffer.clear();

    ensureNativePeer();
    nativeRefill(nativePeer, newItemsPeers, onAppKit);
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
    if (actionDelegate != null) {
      if (USE_STUB) {
        // NOTE: must add stub item when menu opens (otherwise AppKit considers it as empty and we can't fill it later)
        addItem(new MenuItem(), false/*already on AppKit thread*/);
        ApplicationManager.getApplication().invokeLater(()->{
          actionDelegate.run();
          endFill(true);
        });
      } else {
        invokeWithLWCToolkit(actionDelegate, ()->endFill(false/*already on AppKit thread*/), myComponent);
      }
    }
  }

  public void invokeMenuClosing() {
    // Called on AppKit when menu closed

    // When user selects item of system menu (under macOS) AppKit generates such sequence: CloseParentMenu -> PerformItemAction
    // So we can destroy menu-item before item's action performed, and because of that action will not be executed.
    // Defer clearing to avoid this problem.
    disposeChildren(1000);
    if (myOnClose != null) invokeWithLWCToolkit(myOnClose, null, myComponent);
  }

  //
  // Native methods
  //

  // Creates native peer (wrapper for NSMenu with corresponding NSMenuItem).
  // User must dealloc it via nativeDisposeMenu after usage.
  // Can be invoked from any thread.
  private native long nativeCreateMenu();

  // Dealloc native peer.
  // Can be invoked from any thread.
  private native long nativeDisposeMenu(long nativePeer);

  // If menu was created but wasn't added into any parent menu then all setters can be invoked from any thread.
  private native void nativeSetTitle(long menuPtr, String title);
  private native void nativeAddItem(long menuPtr, long itemPtr/*MenuItem OR Menu*/, boolean onAppKit);

  // Refill menu
  private native void nativeRefill(long menuPtr, long[] newItems, boolean onAppKit);

  // Refill sharedApplication.mainMenu with new items (except AppMenu, i.e. first item of mainMenu)
  static
  private native void nativeRefillMainMenu(long[] newItems, boolean onAppKit);

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

  @SuppressWarnings("SSBasedInspection")
  static public void refillMainMenu(@NotNull List<MenuItem> newItems) {
    // 1. dispose old root items
    for (MenuItem item: ourRootItems)
      item.dispose();
    ourRootItems.clear();

    // 2. collect new native peers
    long[] newItemsPeers = new long[newItems.size()];
    for (int c = 0; c < newItems.size(); ++c) {
      MenuItem menuItem = newItems.get(c);
      if (menuItem != null) {
        menuItem.ensureNativePeer();
        newItemsPeers[c] = menuItem.nativePeer;
        ourRootItems.add(menuItem);
      }
    }

    // 3. refill in AppKit thread
    nativeRefillMainMenu(newItemsPeers, true);
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
