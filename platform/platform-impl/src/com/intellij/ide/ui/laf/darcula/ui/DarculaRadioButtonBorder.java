// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.darcula.ui;

public class DarculaRadioButtonBorder extends DarculaCheckBoxBorder {
  @Override
  protected String borderWidthPropertyName() {
    return "RadioButton.borderInsets";
  }
}
