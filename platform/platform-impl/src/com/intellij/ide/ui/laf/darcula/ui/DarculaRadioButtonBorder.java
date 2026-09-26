// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.laf.darcula.ui;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class DarculaRadioButtonBorder extends DarculaCheckBoxBorder {
  @Override
  protected String borderWidthPropertyName() {
    return "RadioButton.borderInsets";
  }
}
