// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.laf.intellij;

import com.intellij.ide.ui.laf.darcula.DarculaTableSelectedCellHighlightBorder;
import com.intellij.util.ui.JBUI;

import javax.swing.border.Border;

public class WinIntelliJTableSelectedCellHighlightBorder extends DarculaTableSelectedCellHighlightBorder {
  @Override
  protected Border createInsideBorder() {
    return JBUI.Borders.empty(2);
  }
}
