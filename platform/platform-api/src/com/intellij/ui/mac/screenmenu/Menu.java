// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.screenmenu;

import com.intellij.DynamicBundle;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SimpleTimer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressWarnings("NonPrivateFieldAccessedInSynchronizedContext")
public class Menu extends MenuItem {
  private static final boolean USE_STUB = Boolean.getBoolean("jbScreenMenuBar.useStubItem"); // just for tests/experiments
  private static final int CLOSE_DELAY = Integer.getInteger("jbScreenMenuBar.closeDelay", 500); // in milliseconds
  private static Boolean IS_ENABLED = null;
  private static Menu ourAppMenu = null;
  private final List<MenuItem> myItems = new ArrayList<>();
  private final List<MenuItem> myBuffer = new ArrayList<>();
  private Runnable myOnOpen;
  private Runnable myOnClose; // we assume that can run it only on EDT (to change swing components)
  private Component myComponent;
  private boolean myIsOpened = false;

  long[] myCachedPeers;

  public Menu(String title) {
    setTitle(title);
  }

  private Menu() { }

  // AppMenu is the first menu item (with title = application name) which is filled by OS
  public static Menu getAppMenu() {
    if (ourAppMenu == null) {
      ourAppMenu = new Menu();
      long nsMenu = nativeGetAppMenu(); // returns retained pointer
      ourAppMenu.nativePeer = ourAppMenu.nativeAttachMenu(nsMenu);
      ourAppMenu.isInHierarchy = true;
    }
    return ourAppMenu;
  }

  public static void renameAppMenuItems(@Nullable DynamicBundle replacements) {
    if (replacements == null) return;

    Set<String> keySet = replacements.getResourceBundle().keySet();

    ArrayList<String> replace = new ArrayList<>(keySet.size() * 2);
    for (String title : keySet) {
      replace.add(title);
      replace.add(replacements.getMessage(title));
    }

    //macOS 13.0 Ventura uses Settings instead of Preferences. See IDEA-300314
    if (SystemInfo.isMacOSVentura) {
      replace.add("Prefer.*");
      replace.add("Settings...");
    }

    nativeRenameAppMenuItems(ArrayUtil.toStringArray(replace));
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

  // Search for subitem by title (reg-exp) in native NSMenu peer
  // Returns the index of first child with matched title.
  public int findIndexByTitle(String re) {
    if (re == null || re.isEmpty()) return -1;

    ensureNativePeer();
    return nativeFindIndexByTitle(nativePeer, re);
  }

  // Search for subitem by title (reg-exp) in native NSMenu peer
  // Returns the first child with matched title.
  // NOTE: Always creates java-wrapper for native NSMenuItem (that must be disposed manually)
  synchronized
  public MenuItem findItemByTitle(String re) {
    if (re == null || re.isEmpty()) return null;

    ensureNativePeer();
    long child = nativeFindItemByTitle(nativePeer, re); // returns retained pointer
    return child == 0 ? null : new MenuItem(child);
  }

  @SuppressWarnings("SSBasedInspection")
  void disposeChildren(int delayMs) {
    if (delayMs <= 0) {
      for (MenuItem item : myItems) {
        item.dispose();
      }
    }
    else {
      // Schedule to dispose later.
      // NOTE: Can be invoked in any thread, not only EDT
      final ArrayList<MenuItem> copy = new ArrayList<>(myItems);
      SimpleTimer.getInstance().setUp(() -> {
        for (MenuItem item : copy) {
          item.dispose();
        }
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
    for (MenuItem item : myBuffer) {
      if (item != null) {
        Disposer.dispose(item);
      }
    }
    myBuffer.clear();
  }

  public @Nullable MenuItem add(@Nullable MenuItem item) {
    myBuffer.add(item);
    return item;
  }

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
      }
      else {
        myCachedPeers[c] = 0;
      }
    }
    myBuffer.clear();

    refillImpl(onAppKit);
  }

  synchronized void refillImpl(boolean onAppKit) {
    ensureNativePeer();
    if (myCachedPeers != null) {
      nativeRefill(nativePeer, myCachedPeers, onAppKit);
    }
  }

  synchronized
  public void endFill() {
    endFill(true);
  }

  synchronized
  public void add(MenuItem item, int position, boolean onAppKit) {
    if (position < 0) return;

    ensureNativePeer();
    item.ensureNativePeer();
    nativeInsertItem(nativePeer, item.nativePeer, position, onAppKit);
    myItems.add(item);
    // TODO: fix position inside myItems !!!
  }

  @Override
  synchronized void ensureNativePeer() {
    if (nativePeer == 0) {
      nativePeer = nativeCreateMenu();
    }
  }

  public void invokeOpenLater() {
    // Called on AppKit when menu opening
    myIsOpened = true;
    if (myOnOpen != null) {
      if (USE_STUB) {
        // NOTE: must add stub item when menu opens (otherwise AppKit considers it as empty and we can't fill it later)
        MenuItem stub = new MenuItem();
        myItems.add(stub);
        stub.isInHierarchy = true;

        ensureNativePeer();
        stub.ensureNativePeer();
        nativeAddItem(nativePeer, stub.nativePeer, false/*already on AppKit thread*/);

        ApplicationManager.getApplication().invokeLater(() -> {
          myOnOpen.run();
          endFill(true);
        });
      }
      else {
        invokeWithLWCToolkit(myOnOpen, () -> endFill(false/*already on AppKit thread*/), myComponent);
      }
    }
  }

  public void invokeMenuClosing() {
    // Called on AppKit when menu closed
    myIsOpened = false;

    // When user selects item of system menu (under macOS) AppKit sometimes generates such sequence: CloseParentMenu -> PerformItemAction
    // So we can destroy menu-item before item's action performed, and because of that action will not be executed.
    // Defer clearing to avoid this problem.
    disposeChildren(CLOSE_DELAY);

    // NOTE: we can't perform native cleaning immediately, because items from 'Help' menu stop work.
    SimpleTimer.getInstance().setUp(() -> {
      if (myIsOpened) {
        return;
      }

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

  // Creates native peer (wrapper for NSMenu) with existing NSMenu (must be already retained)
  // User must dealloc it via nativeDispose after usage.
  // Can be invoked from any thread.
  private native long nativeAttachMenu(long nsMenu);

  // Find methods
  // Always performs on AppKit thread with waiting for result
  private native long nativeFindItemByTitle(long menuPtr, String re); // NOTE: returns retained pointer

  private native int nativeFindIndexByTitle(long menuPtr, String re);

  // Modification methods
  // If menu was created but wasn't added into any parent menu then all setters can be invoked from any thread.
  private native void nativeSetTitle(long menuPtr, String title, boolean onAppKit);

  private native void nativeAddItem(long menuPtr, long itemPtr/*MenuItem OR Menu*/, boolean onAppKit);

  private native void nativeInsertItem(long menuPtr, long itemPtr/*MenuItem OR Menu*/, int position, boolean onAppKit);

  // Refill menu.
  // If menuPtr == null then refills sharedApplication.mainMenu with new items (except AppMenu, i.e. first item of mainMenu)
  native void nativeRefill(long menuPtr, long[] newItems, boolean onAppKit);

  static
  private native void nativeInitClass();

  // NOTE: returns retained pointer
  static
  private native long nativeGetAppMenu();

  static
  private native void nativeRenameAppMenuItems(String[] replacements);

  //
  // Static API
  //

  public static boolean isJbScreenMenuEnabled() {
    if (IS_ENABLED != null) {
      return IS_ENABLED;
    }

    IS_ENABLED = false;

    if (!SystemInfoRt.isMac || !Boolean.getBoolean("jbScreenMenuBar.enabled")) {
      return false;
    }
    if (Boolean.getBoolean("apple.laf.useScreenMenuBar")) {
      Logger.getInstance(Menu.class).info("apple.laf.useScreenMenuBar==true, default screen menu implementation will be used");
      return false;
    }

    Path lib = PathManager.findBinFile("libmacscreenmenu64.dylib");
    try {
      System.load(lib.toString());
      nativeInitClass();
      // create and dispose native object (just for to test)
      Menu test = new Menu("test");
      test.ensureNativePeer();
      Disposer.dispose(test);
      IS_ENABLED = true;
      Logger.getInstance(Menu.class).info("use new ScreenMenuBar implementation");
    }
    catch (Throwable e) {
      // default screen menu implementation will be used
      Logger.getInstance(Menu.class).warn("can't load menu library: " + lib + ", exception: " + e.getMessage());
    }

    return IS_ENABLED;
  }

  private static void invokeWithLWCToolkit(Runnable r, Runnable after, Component invoker) {
    try {
      Class<?> toolkitClass = Class.forName("sun.lwawt.macosx.LWCToolkit");
      Method invokeMethod = ReflectionUtil.getDeclaredMethod(toolkitClass, "invokeAndWait", Runnable.class, Component.class, boolean.class, int.class);
      if (invokeMethod != null) {
        try {
          invokeMethod.invoke(toolkitClass, r, invoker, true, -1);
          invokeMethod.invoke(toolkitClass, r, invoker);
        }
        catch (Exception e) {
          // suppress InvocationTargetException as in openjdk implementation (see com.apple.laf.ScreenMenu.java)
          Logger.getInstance(Menu.class).debug("invokeWithLWCToolkit.invokeAndWait: " + e);
        }
        if (after != null) after.run();
      }
      else {
        Logger.getInstance(Menu.class).warn("can't find sun.lwawt.macosx.LWCToolkit.invokeAndWait, screen menu won't be filled");
      }
    }
    catch (ClassNotFoundException e) {
      Logger.getInstance(Menu.class).warn("can't find sun.lwawt.macosx.LWCToolkit, screen menu won't be filled");
    }
  }
}
