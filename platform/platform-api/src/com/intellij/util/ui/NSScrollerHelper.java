// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Callback;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.EventListener;
import java.util.Iterator;
import java.util.List;

@ApiStatus.Internal
public final class NSScrollerHelper {
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

  @ApiStatus.Internal
  public enum ClickBehavior {NextPage, JumpToSpot}

  @ApiStatus.Internal
  public enum Style {Legacy, Overlay}

  private static ClickBehavior ourClickBehavior;
  private static final List<Reference<ScrollbarStyleListener>> ourStyleListeners = new ArrayList<>();

  static {
    if (SystemInfoRt.isMac) {
      initNotificationObserver();
      updateBehaviorPreferences();
    }
  }

  private static boolean isOverlayScrollbarSupported() {
    return SystemInfoRt.isMac;
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
    ID delegate = Foundation.invoke("NSScrollerChangesObserver", "new");

    try {
      ID center;
      center = Foundation.invoke("NSNotificationCenter", "defaultCenter");
      Foundation.invoke(center, "addObserver:selector:name:object:",
             delegate,
             Foundation.createSelector("handleScrollerStyleChanged:"),
             Foundation.nsString("NSPreferredScrollerStyleDidChangeNotification"),
             ID.NIL
      );

      center = Foundation.invoke("NSDistributedNotificationCenter", "defaultCenter");
      Foundation.invoke(center, "addObserver:selector:name:object:",
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

  @ApiStatus.Internal
  public static @Nullable ClickBehavior getClickBehavior() {
    if (!SystemInfoRt.isMac) return null;
    return ourClickBehavior;
  }

  private static void updateBehaviorPreferences() {
    if (!SystemInfoRt.isMac) return;

    Foundation.NSAutoreleasePool pool = new Foundation.NSAutoreleasePool();
    try {
      ID defaults = Foundation.invoke("NSUserDefaults", "standardUserDefaults");
      Foundation.invoke(defaults, "synchronize");
      ourClickBehavior = Foundation.invoke(defaults, "boolForKey:", Foundation.nsString("AppleScrollerPagingBehavior")).booleanValue()
                         ? ClickBehavior.JumpToSpot : ClickBehavior.NextPage;
    }
    finally {
      pool.drain();
    }
  }

  @ApiStatus.Internal
  public static @Nullable Style getScrollerStyle() {
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

  @ApiStatus.Internal
  public static void addScrollbarStyleListener(@NotNull ScrollbarStyleListener listener) {
    processReferences(listener, null, null);
  }

  @ApiStatus.Internal
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

  @ApiStatus.Internal
  public interface ScrollbarStyleListener extends EventListener {
    void styleChanged();
  }
}
