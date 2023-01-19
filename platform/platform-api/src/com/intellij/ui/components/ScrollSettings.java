// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.diagnostic.LoadingState;
import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.RemoteDesktopService;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.registry.Registry;

import javax.swing.*;
import java.awt.*;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

final class ScrollSettings {
  static boolean isEligibleFor(Component component) {
    if (component == null || !component.isShowing() || !LoadingState.COMPONENTS_REGISTERED.isOccurred()) {
      return false;
    }

    Application application = getApplication();
    if (application == null || PowerSaveMode.isEnabled() || RemoteDesktopService.isRemoteSession()) {
      return false;
    }

    UISettings settings = UISettings.getInstanceOrNull();
    return settings != null && settings.getSmoothScrolling();
  }

  static boolean isHighPrecisionEnabled() {
    return Registry.is("idea.true.smooth.scrolling.high.precision", true);
  }

  static boolean isPixelPerfectEnabled() {
    return Registry.is("idea.true.smooth.scrolling.pixel.perfect", true);
  }

  static boolean isDebugEnabled() {
    return Registry.is("idea.true.smooth.scrolling.debug", false);
  }

  static boolean isBackgroundFromView() {
    return Registry.is("ide.scroll.background.auto", true);
  }

  static boolean isHeaderOverCorner(JViewport viewport) {
    Component view = viewport == null ? null : viewport.getView();
    return !isNotSupportedYet(view) && Registry.is("ide.scroll.layout.header.over.corner", true);
  }

  static boolean isNotSupportedYet(Component view) {
    return view instanceof JTable;
  }

  static boolean isGapNeededForAnyComponent() {
    return Registry.is("ide.scroll.align.component", true);
  }

  static boolean isHorizontalGapNeededOnMac() {
    return Registry.is("mac.scroll.horizontal.gap", false);
  }

  static boolean isThumbSmallIfOpaque() {
    return Registry.is("ide.scroll.thumb.small.if.opaque", false);
  }

  /* A heuristics that disables scrolling interpolation in diff / merge windows.
     We need to make scrolling synchronization compatible with the interpolation first.

     NOTE: The implementation is a temporary, ad-hoc heuristics that is needed solely to
           facilitate testing of the experimental "true smooth scrolling" feature. */
  static boolean isInterpolationEligibleFor(JScrollBar scrollbar) {
    Window window = (Window)scrollbar.getTopLevelAncestor();

    if (window instanceof JDialog && "Commit Changes".equals(((JDialog)window).getTitle())) {
      return false;
    }

    if (!(window instanceof RootPaneContainer)) {
      return true;
    }

    Component[] components = ((RootPaneContainer)window).getContentPane().getComponents();

    if (components.length == 1 && components[0].getClass().getName().contains("DiffWindow")) {
      return false;
    }

    if (components.length == 2 && components[0] instanceof Container) {
      Component[] subComponents = ((Container)components[0]).getComponents();
      if (subComponents.length == 1) {
        String name = subComponents[0].getClass().getName();
        if (name.contains("DiffWindow") || name.contains("MergeWindow")) {
          return false;
        }
      }
    }

    return true;
  }
}
