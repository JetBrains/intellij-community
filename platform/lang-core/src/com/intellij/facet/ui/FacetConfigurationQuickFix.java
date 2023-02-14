// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.ui;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class FacetConfigurationQuickFix {
  private final @NlsContexts.Button String myFixButtonText;

  protected FacetConfigurationQuickFix() {
    this(null);
  }

  protected FacetConfigurationQuickFix(final @Nullable @NlsContexts.Button String fixButtonText) {
    myFixButtonText = fixButtonText;
  }

  @Nullable
  public final @NlsContexts.Button String getFixButtonText() {
    return myFixButtonText;
  }

  public abstract void run(JComponent place);

}
