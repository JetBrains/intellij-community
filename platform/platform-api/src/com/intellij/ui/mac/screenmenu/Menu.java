// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.screenmenu;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
public class Menu extends MenuItem {
  private static Boolean IS_ENABLED = null;
  private static final List<MenuItem> ourRootItems = new ArrayList<>();
  private final List<MenuItem> myItems = new ArrayList<>();

  public Menu(String title) {
    setTitle(title);
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

  synchronized
  public void refill(@NotNull List<MenuItem> newItems) {
    disposeChildren(0); // NOTE: to test/increase stability use 2000 ms

    long[] newItemsPeers = new long[newItems.size()];
    //System.err.println("refill with " + newItemsPeers.length + " items");
    for (int c = 0; c < newItems.size(); ++c) {
      MenuItem menuItem = newItems.get(c);
      if (menuItem != null) {
        menuItem.ensureNativePeer();
        newItemsPeers[c] = menuItem.nativePeer;
        //System.err.printf("\t0x%X\n", newItemsPeers[c]);
        myItems.add(menuItem);
      } else {
        newItemsPeers[c] = 0;
      }
    }

    ensureNativePeer();
    nativeRefill(nativePeer, newItemsPeers);
  }

  @Override
  synchronized
  void ensureNativePeer() {
    if (nativePeer == 0) {
      nativePeer = nativeCreateMenu();
    }
  }

  public void invokeOpenLater() {
    if (actionDelegate != null)
      actionDelegate.run();
  }

  public void invokeMenuClosing() {
    // When user selects item of system menu (under macOS) AppKit generates such sequence: CloseParentMenu -> PerformItemAction
    // So we can destroy menu-item before item's action performed, and because of that action will not be executed.
    // Defer clearing to avoid this problem.
    disposeChildren(1000);
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
  // schedules action to perform on AppKit thread
  private native void nativeRefill(long menuPtr, long[] newItems);

  // Refill sharedApplication.mainMenu with new items (except AppMenu, i.e. first item of mainMenu)
  // schedules action to perform on AppKit thread
  static
  private native void nativeRefillMainMenu(long[] newItems);

  //
  // Static API
  //

  public static boolean isEnabled() {
    if (IS_ENABLED != null)
      return IS_ENABLED;

    IS_ENABLED = false;
    if (SystemInfo.isMacSystemMenu && !Boolean.getBoolean("disableJbScreenMenuBar")) {
      Field disableJbScreenMenuBarField = null;
      try {
        Class aquaMenuBarUIClass = Class.forName("com.apple.laf.AquaMenuBarUI");
        disableJbScreenMenuBarField = aquaMenuBarUIClass.getDeclaredField("disableJbScreenMenuBar");
      } catch (ClassNotFoundException e) {
        Logger.getInstance(Menu.class).info("new screen menu: AquaMenuBarUI not found, " + e.getMessage());
      } catch (NoSuchFieldException e) {
        Logger.getInstance(Menu.class).info("new screen menu: AquaMenuBarUI.disableJBScreenMenuBar not found, can't use new menu impl, "
                                            + e.getMessage());
      }

      if (disableJbScreenMenuBarField == null) {
        Logger.getInstance(Menu.class).info("new screen menu: disableJbScreenMenu isn't supported by runtime, new screen menu is disabled");
      } else {
        Path lib = PathManager.findBinFile("libmacscreenmenu64.dylib");
        try {
          System.load(lib.toFile().getAbsolutePath());
          IS_ENABLED = true;
          Logger.getInstance(Menu.class).info("use new screen menu");
          // create and dispose native object (just for to test)
          Menu test = new Menu("test");
          test.ensureNativePeer();
          test.dispose();
        } catch (Throwable e) {
          Logger.getInstance(Menu.class).info("can't load menu library: " + lib.toFile().getAbsolutePath() + ", exception: " + e.getMessage());
        }
      }
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
    nativeRefillMainMenu(newItemsPeers);
  }
}
