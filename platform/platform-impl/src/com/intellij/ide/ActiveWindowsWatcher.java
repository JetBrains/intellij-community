// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.wm.ex.ProjectFrameCapabilitiesService;
import com.intellij.openapi.wm.ex.ProjectFrameCapability;
import com.intellij.openapi.wm.impl.ProjectFrameHelper;
import com.intellij.ui.AppUIUtil;
import com.intellij.ui.ComponentUtil;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.Dialog;
import java.awt.Frame;
import java.awt.Window;
import java.util.Iterator;
import java.util.LinkedHashSet;

@ApiStatus.Internal
public final class ActiveWindowsWatcher {
  private static final LinkedHashSet<Window> activatedWindows = new LinkedHashSet<>();

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
    if (SystemInfoRt.isMac && SystemInfo.isJetBrainsJvm && activatedWindows.size() > 1) {
      return activatedWindows.stream()
        .filter(window -> window == w || isIncludedInWindowSwitchOrder(window))
        .filter(window -> window == w || Foundation.invoke(MacUtil.getWindowFromJavaWindow(window), "isOnActiveSpace").booleanValue())
        .toArray(Window[]::new);
    }

    return activatedWindows.stream()
      .filter(window -> window == w || isIncludedInWindowSwitchOrder(window))
      .toArray(Window[]::new);
  }

  private static boolean isIncludedInWindowSwitchOrder(@NotNull Window window) {
    ProjectFrameHelper frameHelper = ProjectFrameHelper.getFrameHelper(window);
    if (frameHelper == null) {
      return true;
    }

    Project project = frameHelper.getProject();
    if (project == null || project.isDisposed()) {
      return true;
    }

    @SuppressWarnings("deprecation")
    ProjectFrameCapabilitiesService capabilitiesService = ProjectFrameCapabilitiesService.Companion.getInstanceSync();
    return !capabilitiesService.has(project, ProjectFrameCapability.EXCLUDE_FROM_WINDOW_SWITCH_ORDER);
  }
}
