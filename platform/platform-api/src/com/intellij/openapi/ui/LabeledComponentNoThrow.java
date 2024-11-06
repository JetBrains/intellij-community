// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;

/**
 * Special type of LabeledComponent that does not throw exceptions in setComponentClass.
 * This class was made to retain source compatibility with LabeledComponent while fixing
 * IJI-2340 Remove UI Designer forms compilation
 */
@ApiStatus.Internal
public class LabeledComponentNoThrow<Comp extends JComponent> extends LabeledComponent<Comp> {
  @Override
  public void setComponentClass(String className) {
    try {
      super.setComponentClass(className);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
