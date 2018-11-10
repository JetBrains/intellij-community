// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ui.components.labels.LinkLabel;

import java.awt.*;

/**
 * @author Alexander Lobas
 */
public class LinkComponent extends LinkLabel {
  public LinkComponent() {
    super("", null);
  }

  @Override
  protected Color getTextColor() {
    return getForeground();
  }
}