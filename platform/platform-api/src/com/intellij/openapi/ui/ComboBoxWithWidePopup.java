/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.ui;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.util.Vector;
import java.awt.*;

public class ComboBoxWithWidePopup extends JComboBox {

  private boolean myLayingOut = false;

  public ComboBoxWithWidePopup(final ComboBoxModel aModel) {
    super(aModel);

    if (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel()) setMaximumRowCount(25);
  }

  public ComboBoxWithWidePopup(final Object items[]) {
    super(items);

    if (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel()) setMaximumRowCount(25);
  }

  public ComboBoxWithWidePopup(final Vector<?> items) {
    super(items);

    if (SystemInfo.isMac && UIUtil.isUnderAquaLookAndFeel()) setMaximumRowCount(25);
  }

  public ComboBoxWithWidePopup() {
  }

  public void doLayout() {
    try {
      myLayingOut = true;
      super.doLayout();
    }
    finally {
      myLayingOut = false;
    }
  }

  public Dimension getSize() {
    Dimension size = super.getSize();
    if (!myLayingOut) {
      size.width = Math.max(size.width, getOriginalPreferredSize().width);
    }
    return size;
  }

  protected Dimension getOriginalPreferredSize() {
    return getPreferredSize();
  }
}
