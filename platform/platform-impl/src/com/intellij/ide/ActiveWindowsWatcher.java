// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ComponentUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.Iterator;
import java.util.LinkedHashSet;

public final class ActiveWindowsWatcher {

  private final static LinkedHashSet<Window> activatedWindows = new LinkedHashSet<>();

  public static boolean isTheCurrentWindowOnTheActivatedList(@NotNull Window w) {
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
      if (!window.isFocusableWindow() || !window.isVisible() || ComponentUtil.isMinimized(window) || AppUIUtil.isInFullscreen(window)) {
        iter.remove();
      }
    }
    // The list can be empty if all windows are in fullscreen or minimized state
  }

  public static Window nextWindowAfter (@NotNull Window w) {
    assert activatedWindows.contains(w);

    Window[] windows = activatedWindows.toArray(new Window[0]);

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

    Window[] windows = activatedWindows.toArray(new Window[0]);

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
}
