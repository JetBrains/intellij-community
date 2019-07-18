// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf;

import com.intellij.ide.ui.laf.darcula.DarculaLaf;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.util.registry.RegistryValueListener;
import com.intellij.ui.mac.foundation.Foundation;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.plaf.basic.BasicLookAndFeel;
import javax.swing.plaf.metal.DefaultMetalTheme;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class IntelliJLaf extends DarculaLaf {
  @Override
  public String getName() {
    return "IntelliJ";
  }

  @Override
  @NotNull
  protected String getPrefix() {
    return UIUtil.isUnderWin10LookAndFeel() ? "intellijlaf_native" : "intellijlaf";
  }

  @Override
  @Nullable
  protected String getSystemPrefix() {
    if (SystemInfo.isLinux) {
      return super.getSystemPrefix();
    } else if (SystemInfo.isWindows) {
      return UIUtil.isUnderWin10LookAndFeel() ? null : getPrefix() + "_windows";
    } else if (SystemInfo.isMac) {
      return UIUtil.isUnderDefaultMacTheme() ? getPrefix() + "_mac" : null;
    } else {
      return null;
    }
  }

  @Override
  protected BasicLookAndFeel createBaseLookAndFeel() {
    Registry.get("ide.intellij.laf.win10.ui").addListener(new RegistryValueListener.Adapter() {
      @Override
      public void afterValueChanged(@NotNull RegistryValue value) {
        try { // Update UI
          UIManager.setLookAndFeel(UIManager.getLookAndFeel());
        } catch (UnsupportedLookAndFeelException ignored) {}
      }
    }, myDisposable);

    return super.createBaseLookAndFeel();
  }

  @Override
  protected DefaultMetalTheme createMetalTheme() {
    return new IdeaBlueMetalTheme();
  }

  public static Color getSelectedControlColor() {
    // https://developer.apple.com/library/mac/e/Cocoa/Reference/ApplicationKit/Classes/NSColor_Class/#//apple_ref/occ/clm/NSColor/alternateSelectedControlColor
    return MacUtil.colorFromNative(Foundation.invoke("NSColor", "alternateSelectedControlColor"));
  }
}
