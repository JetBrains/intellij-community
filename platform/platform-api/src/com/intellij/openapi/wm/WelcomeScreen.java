// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm;

import com.intellij.openapi.Disposable;

import javax.swing.*;

/**
 * @author Konstantin Bulenkov
 */
public interface WelcomeScreen extends Disposable {
  JComponent getWelcomePanel();

  default void setupFrame(JFrame frame) {
  }
}
