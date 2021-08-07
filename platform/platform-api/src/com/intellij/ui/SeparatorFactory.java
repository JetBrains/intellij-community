// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;


public final class SeparatorFactory {
  private SeparatorFactory() {
  }

  public static TitledSeparator createSeparator(@NlsContexts.Separator String text, @Nullable JComponent labelFor) {
    return new TitledSeparator(text, labelFor);
  }
}