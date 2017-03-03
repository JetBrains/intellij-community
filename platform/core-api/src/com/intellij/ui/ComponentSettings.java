/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;

import javax.swing.*;
import java.awt.*;

/**
 * IDE-agnostic component settings.
 */
public class ComponentSettings {
  private static final boolean SUPPORTED_JAVA = SystemInfo.isJetbrainsJvm || SystemInfo.isJavaVersionAtLeast("1.9");
  private static final RegistryValue SUPPORTED = Registry.get("ide.scroll.precise");

  private static final RegistryValue HIGH_PRECISION_SCROLLING = Registry.get("idea.true.smooth.scrolling.high.precision");

  private static final RegistryValue PIXEL_PERFECT_SCROLLING = Registry.get("idea.true.smooth.scrolling.pixel.perfect");

  private boolean mySmoothScrollingEnabled = true;
  private boolean myRemoteDesktopConnected;
  private boolean myPowerSaveModeEnabled;

  private static final ComponentSettings ourInstance = new ComponentSettings();

  public static ComponentSettings getInstance() {
    return ourInstance;
  }

  // Returns whether "true smooth scrolling" is applicable to the particular component
  public boolean isTrueSmoothScrollingEligibleFor(Component component) {
    return isSmoothScrollingSupported() &&
           !ApplicationManager.getApplication().isUnitTestMode() &&
           mySmoothScrollingEnabled &&
           !myRemoteDesktopConnected &&
           !myPowerSaveModeEnabled &&
           component != null &&
           component.isShowing();
  }

  // Returns whether smooth scrolling supported
  public boolean isSmoothScrollingSupported() {
    return SUPPORTED.asBoolean() || (SUPPORTED_JAVA && SystemInfo.isMac);
  }

  // Returns whether high-precision scrolling events are enabled
  public boolean isHighPrecisionScrollingEnabled() {
    return HIGH_PRECISION_SCROLLING.asBoolean();
  }

  // Returns whether pixel-perfect scrolling events are enabled (requires high-precision events to be effective)
  public boolean isPixelPerfectScrollingEnabled() {
    return PIXEL_PERFECT_SCROLLING.asBoolean();
  }

  /* A heuristics that disables scrolling interpolation in diff / merge windows.
     We need to to make scrolling synchronization compatible with the interpolation first.

     NOTE: The implementation is a temporary, ad-hoc heuristics that is needed solely to
           facilitate testing of the experimental "true smooth scrolling" feature. */
  public boolean isInterpolationEligibleFor(JScrollBar scrollbar) {
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

  public void setSmoothScrollingEnabled(boolean enabled) {
    mySmoothScrollingEnabled = enabled;
  }

  public void setRemoteDesktopConnected(boolean connected) {
    myRemoteDesktopConnected = connected;
  }

  public void setPowerSaveModeEnabled(boolean enabled) {
    myPowerSaveModeEnabled = enabled;
  }
}
