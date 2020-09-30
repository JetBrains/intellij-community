// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.ide.BrowserUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

public class BrowserHyperlinkListener extends HyperlinkAdapter {
  public static final BrowserHyperlinkListener INSTANCE = new BrowserHyperlinkListener();

  @Override
  protected void hyperlinkActivated(@NotNull HyperlinkEvent e) {
    BrowserUtil.browse(e.getDescription());
  }
}
