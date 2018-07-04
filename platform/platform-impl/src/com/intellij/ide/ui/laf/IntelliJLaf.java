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
      public void afterValueChanged(RegistryValue value) {
        try { // Update UI
          UIManager.setLookAndFeel(UIManager.getLookAndFeel());
        } catch (UnsupportedLookAndFeelException ignored) {}
      }
    }, myDisposable);

    if (UIUtil.isUnderWindowsLookAndFeel()) {
      try {
        final String name = UIManager.getSystemLookAndFeelClassName();
        return (BasicLookAndFeel)Class.forName(name).newInstance();
      }
      catch (Exception e) {
        log(e);
      }
    }
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
