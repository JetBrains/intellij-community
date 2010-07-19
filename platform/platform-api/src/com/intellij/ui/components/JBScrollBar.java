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

import com.intellij.util.ui.ButtonlessScrollBarUI;

import javax.swing.*;

public class JBScrollBar extends JScrollBar{
  public JBScrollBar() {
    init();
  }

  public JBScrollBar(int orientation) {
    super(orientation);
    init();
  }

  public JBScrollBar(int orientation, int value, int extent, int min, int max) {
    super(orientation, value, extent, min, max);
    init();
  }

  private void init() {
    putClientProperty("JScrollBar.fastWheelScrolling", Boolean.TRUE); // fast scrolling for JDK 6
    setFocusable(false);
  }

  @Override
  public void updateUI() {
    setUI(ButtonlessScrollBarUI.createNormal());
  }
}
