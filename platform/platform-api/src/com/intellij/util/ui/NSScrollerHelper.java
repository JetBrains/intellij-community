// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui;

import com.intellij.openapi.util.SystemInfoRt;
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
    MacScrollbarPreferences.observeStyleChanges(NSScrollerHelper::fireStyleChanged);
    MacScrollbarPreferences.observeBehaviorChanges(NSScrollerHelper::updateBehaviorPreferences);
  }

  @ApiStatus.Internal
  public static @Nullable ClickBehavior getClickBehavior() {
    if (!SystemInfoRt.isMac) return null;
    return ourClickBehavior;
  }

  private static void updateBehaviorPreferences() {
    if (!SystemInfoRt.isMac) return;

    ourClickBehavior = MacScrollbarPreferences.isJumpToSpot() ? ClickBehavior.JumpToSpot : ClickBehavior.NextPage;
  }

  @ApiStatus.Internal
  public static @NotNull Style getScrollerStyle() {
    if (!isOverlayScrollbarSupported()) return Style.Overlay;

    try {
      if (MacScrollbarPreferences.getPreferredStyle() == 1) {
        return Style.Overlay;
      }
    }
    catch (Throwable ignore) {
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
