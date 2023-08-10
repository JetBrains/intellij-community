// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedHashSet;

public final class ActiveWindowsWatcher {
  private final static LinkedHashSet<Window> activatedWindows = new LinkedHashSet<>();

  public static boolean isTheCurrentWindowOnTheActivatedList(Window w) {
    updateActivatedWindowSet();
    return activatedWindows.contains(w);
  }

  public static void addActiveWindow (Window window) {
    activatedWindows.add(window);
    updateActivatedWindowSet();
  }

  public static void updateActivatedWindowSet() {
    for (Iterator<Window> iter = activatedWindows.iterator(); iter.hasNext(); ) {
      Window window = iter.next();
      if (!window.isFocusableWindow() ||
          !window.isVisible() ||
          ComponentUtil.isMinimized(window) ||
          AppUIUtil.isInFullScreen(window) ||
          (window instanceof Frame && ((Frame) window).isUndecorated()) ||
          (window instanceof Dialog && ((Dialog) window).isUndecorated()) ||
           UIUtil.isSimpleWindow(window)
      ) {
        iter.remove();
      }
    }
    // The list can be empty if all windows are in fullscreen or minimized state
  }

  public static Window nextWindowAfter (@NotNull Window w) {

    Window[] windows = getWindows(w);

    if (w.equals(windows[windows.length - 1])) {
      return windows[0];
    }

    for (int i = (windows.length - 2); i >= 0; i--) {
      if (w.equals(windows[i])) {
        return windows[i + 1];
      }
    }

    throw new IllegalArgumentException("The window after "  + w.getName() +  " has not been found");
  }

  public static Window nextWindowBefore (@NotNull Window w) {
    assert activatedWindows.contains(w);

    Window[] windows = getWindows(w);

    if (w.equals(windows[0])) {
      return windows[windows.length - 1];
    }

    for (int i = 1; i < windows.length; i++) {
      if (w.equals(windows[i])) {
        return windows[i - 1];
      }
    }

    throw new IllegalArgumentException("The window after "  + w.getName() +  " has not been found");
  }

  private static Window @NotNull [] getWindows(@NotNull Window w) {
    if (SystemInfo.isMac && SystemInfo.isJetBrainsJvm && activatedWindows.size() > 1) {
      return activatedWindows.stream()
        .filter(window -> window == w || Foundation.invoke(MacUtil.getWindowFromJavaWindow(window), "isOnActiveSpace").booleanValue())
        .toArray(Window[]::new);
    }

    return activatedWindows.toArray(new Window[0]);
  }
}
