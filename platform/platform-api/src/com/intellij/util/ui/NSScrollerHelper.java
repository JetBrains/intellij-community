// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.Iterator;
import java.util.List;

import static com.intellij.ui.mac.foundation.Foundation.invoke;

class NSScrollerHelper {
  private static final Callback APPEARANCE_CALLBACK = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public void callback(ID self, Pointer selector, ID event) {
      UIUtil.invokeLaterIfNeeded(() -> fireStyleChanged());
    }
  };
  private static final Callback BEHAVIOR_CALLBACK = new Callback() {
    @SuppressWarnings("UnusedDeclaration")
    public void callback(ID self, Pointer selector, ID event) {
      UIUtil.invokeLaterIfNeeded(() -> updateBehaviorPreferences());
    }
  };

  public enum ClickBehavior {NextPage, JumpToSpot}

  public enum Style {Legacy, Overlay}

  private static ClickBehavior ourClickBehavior = null;
  private static final List<Reference<ScrollbarStyleListener>> ourStyleListeners = new ArrayList<>();

  static {
    if (SystemInfo.isMac) {
      initNotificationObserver();
      updateBehaviorPreferences();
    }
  }

  private static boolean isOverlayScrollbarSupported() {
    return SystemInfo.isMac && SystemInfo.isMacOSMountainLion;
  }

  private static void initNotificationObserver() {
    Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();

    ID delegateClass = Foundation.allocateObjcClassPair(Foundation.getObjcClass("NSObject"), "NSScrollerChangesObserver");
    if (!ID.NIL.equals(delegateClass)) {
      // This static initializer might be called more than once (with different class loaders). In that case NSScrollerChangesObserver
      // already exists.
      if (!Foundation.addMethod(delegateClass, Foundation.createSelector("handleScrollerStyleChanged:"), APPEARANCE_CALLBACK, "v@")) {
        throw new RuntimeException("Cannot add observer method");
      }
      if (!Foundation.addMethod(delegateClass, Foundation.createSelector("handleBehaviorChanged:"), BEHAVIOR_CALLBACK, "v@")) {
        throw new RuntimeException("Cannot add observer method");
      }

      Foundation.registerObjcClassPair(delegateClass);
    }
    ID delegate = invoke("NSScrollerChangesObserver", "new");

    try {
      ID center;
      center = invoke("NSNotificationCenter", "defaultCenter");
      invoke(center, "addObserver:selector:name:object:",
             delegate,
             Foundation.createSelector("handleScrollerStyleChanged:"),
             Foundation.nsString("NSPreferredScrollerStyleDidChangeNotification"),
             ID.NIL
      );

      center = invoke("NSDistributedNotificationCenter", "defaultCenter");
      invoke(center, "addObserver:selector:name:object:",
             delegate,
             Foundation.createSelector("handleBehaviorChanged:"),
             Foundation.nsString("AppleNoRedisplayAppearancePreferenceChanged"),
             ID.NIL,
             2 // NSNotificationSuspensionBehaviorCoalesce
      );
    }
    finally {
      pool.drain();
    }
  }

  @Nullable
  public static ClickBehavior getClickBehavior() {
    if (!SystemInfo.isMac) return null;
    return ourClickBehavior;
  }

  private static void updateBehaviorPreferences() {
    if (!SystemInfo.isMac) return;

    Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      ID defaults = invoke("NSUserDefaults", "standardUserDefaults");
      invoke(defaults, "synchronize");
      ourClickBehavior = invoke(defaults, "boolForKey:", Foundation.nsString("AppleScrollerPagingBehavior")).intValue() == 1
                         ? ClickBehavior.JumpToSpot : ClickBehavior.NextPage;
    }
    finally {
      pool.drain();
    }
  }

  @Nullable
  public static Style getScrollerStyle() {
    if (!isOverlayScrollbarSupported()) return null;

    Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      if (Foundation.invoke(Foundation.getObjcClass("NSScroller"), "preferredScrollerStyle").intValue() == 1) {
        return Style.Overlay;
      }
    }
    catch (Throwable ignore) {
    }
    finally {
      pool.drain();
    }
    return Style.Legacy;
  }

  public static void addScrollbarStyleListener(@NotNull ScrollbarStyleListener listener) {
    processReferences(listener, null, null);
  }

  public static void removeScrollbarStyleListener(@NotNull ScrollbarStyleListener listener) {
    processReferences(null, listener, null);
  }

  private static void processReferences(ScrollbarStyleListener toAdd, ScrollbarStyleListener toRemove, List<? super ScrollbarStyleListener> list) {
    synchronized (ourStyleListeners) {
      Iterator<Reference<ScrollbarStyleListener>> iterator = ourStyleListeners.iterator();
      while (iterator.hasNext()) {
        Reference<ScrollbarStyleListener> reference = iterator.next();
        ScrollbarStyleListener ui = reference.get();
        if (ui == null || ui == toRemove) {
          iterator.remove();
        }
        else if (list != null) {
          list.add(ui);
        }
      }
      if (toAdd != null) {
        ourStyleListeners.add(new WeakReference<>(toAdd));
      }
    }
  }

  private static void fireStyleChanged() {
    List<ScrollbarStyleListener> list = new ArrayList<>();
    processReferences(null, null, list);
    for (ScrollbarStyleListener listener : list) {
      listener.styleChanged();
    }
  }

  public interface ScrollbarStyleListener extends EventListener {
    void styleChanged();
  }
}
