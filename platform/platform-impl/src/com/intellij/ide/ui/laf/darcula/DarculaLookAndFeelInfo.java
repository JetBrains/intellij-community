// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula;

import com.intellij.ide.IdeBundle;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public final class DarculaLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  @NonNls public static final String CLASS_NAME = DarculaLaf.class.getName();

  public DarculaLookAndFeelInfo() {
    super(IdeBundle.message("idea.dark.look.and.feel"), CLASS_NAME);
  }

  @Override
  public boolean equals(Object obj) {
    return (obj instanceof DarculaLookAndFeelInfo);
  }

  @Override
  public int hashCode() {
    return getName().hashCode();
  }
}
