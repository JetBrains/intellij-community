// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula;

import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.JBColor;

/**
 * @author Konstantin Bulenkov
 */
public final class DarculaInstaller {
  public static void uninstall() {
    performImpl(false);
  }

  public static void install() {
    performImpl(true);
  }

  private static void performImpl(final boolean dark) {
    JBColor.setDark(dark);
    IconLoader.setUseDarkIcons(dark);
  }
}
