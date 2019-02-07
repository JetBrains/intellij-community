// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;

/**
 * @author Alexander Lobas
 */
public class ScrollEventHandler extends EventHandler {
  private final FocusListener myFocusListener = new FocusAdapter() {
    @Override
    public void focusGained(FocusEvent e) {
      get(e).scrollToVisible();
    }
  };

  @Override
  public void add(@NotNull Component component) {
    component.addFocusListener(myFocusListener);
  }
}