// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula;

import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public final class DarculaLookAndFeelInfo extends UIManager.LookAndFeelInfo {
  @NonNls public static final String CLASS_NAME = DarculaLaf.class.getName();

  public DarculaLookAndFeelInfo() {
    super("Darcula", CLASS_NAME);
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
