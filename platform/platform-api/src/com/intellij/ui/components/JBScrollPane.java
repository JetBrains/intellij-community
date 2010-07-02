/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.ButtonlessScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.ScrollPaneUI;
import java.awt.*;

public class JBScrollPane extends JScrollPane {
  public JBScrollPane() {
    init();
  }

  public JBScrollPane(Component view) {
    super(view);
    init();
  }

  public JBScrollPane(int vsbPolicy, int hsbPolicy) {
    super(vsbPolicy, hsbPolicy);
    init();
  }

  public JBScrollPane(Component view, int vsbPolicy, int hsbPolicy) {
    super(view, vsbPolicy, hsbPolicy);
    init();
  }

  private void init() {
    setBorder(IdeBorderFactory.createSimpleBorder());
  }

  public void setUI(ScrollPaneUI ui) {
    super.setUI(ui);
    setViewportBorder(new EmptyBorder(1, 1, 1, 1));
  }

  @Override
  public JScrollBar createVerticalScrollBar() {
    return new MyScrollBar(JScrollBar.VERTICAL);
  }

  @Override
  public JScrollBar createHorizontalScrollBar() {
    return new MyScrollBar(JScrollBar.HORIZONTAL);
  }

  private class MyScrollBar extends ScrollBar {
    public MyScrollBar(int orientation) {
      super(orientation);
    }

    @Override
    public void updateUI() {
      setUI(ButtonlessScrollBarUI.createNormal());
    }
  }
}
