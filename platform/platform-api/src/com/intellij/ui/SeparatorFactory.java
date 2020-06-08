// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author yole
 */
public final class SeparatorFactory {
  private SeparatorFactory() {
  }

  public static TitledSeparator createSeparator(String text, @Nullable JComponent labelFor) {
    return new TitledSeparator(text, labelFor);
  }
}