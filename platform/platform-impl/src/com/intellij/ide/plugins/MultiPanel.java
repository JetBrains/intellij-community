// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ui.CardLayoutPanel;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public abstract class MultiPanel extends CardLayoutPanel<Integer, Integer, JComponent> {
  @Override
  protected Integer prepare(Integer key) {
    return key;
  }

  @Override
  protected JComponent create(Integer key) {
    throw new RuntimeException("Create card, unknown KEY index: " + key);
  }
}