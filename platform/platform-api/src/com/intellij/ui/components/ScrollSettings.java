/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.components;

import com.intellij.ide.PowerSaveMode;
import com.intellij.ide.RemoteDesktopService;
import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;

import javax.swing.JDialog;
import javax.swing.JScrollBar;
import javax.swing.RootPaneContainer;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;

import static com.intellij.openapi.application.ApplicationManager.getApplication;

/**
 * @author Sergey.Malenkov
 */
final class ScrollSettings {
  private static final RegistryValue PIXEL_PERFECT_SCROLLING = Registry.get("idea.true.smooth.scrolling.pixel.perfect");
  private static final RegistryValue HIGH_PRECISION_SCROLLING = Registry.get("idea.true.smooth.scrolling.high.precision");

  static boolean isEligibleFor(Component component) {
    if (component == null || !component.isShowing()) return false;

    Application application = getApplication();
    if (application == null || application.isUnitTestMode()) return false;
    if (PowerSaveMode.isEnabled()) return false;
    if (RemoteDesktopService.isRemoteSession()) return false;

    UISettings settings = UISettings.getInstanceOrNull();
    return settings != null && settings.getSmoothScrolling();
  }

  static boolean isHighPrecisionEnabled() {
    return HIGH_PRECISION_SCROLLING.asBoolean();
  }

  static boolean isPixelPerfectEnabled() {
    return PIXEL_PERFECT_SCROLLING.asBoolean();
  }

  /* A heuristics that disables scrolling interpolation in diff / merge windows.
     We need to to make scrolling synchronization compatible with the interpolation first.

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
