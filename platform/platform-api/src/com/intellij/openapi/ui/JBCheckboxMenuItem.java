/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.util.ui.GraphicsUtil;

import javax.swing.*;
import java.awt.*;

/**
 * User: Vassiliy.Kudryashov
 */
public class JBCheckboxMenuItem extends JCheckBoxMenuItem {
  public JBCheckboxMenuItem() {
    super();
  }

  public JBCheckboxMenuItem(Icon icon) {
    super(icon);
  }

  public JBCheckboxMenuItem(String text) {
    super(text);
  }

  public JBCheckboxMenuItem(Action a) {
    super(a);
  }

  public JBCheckboxMenuItem(String text, Icon icon) {
    super(text, icon);
  }

  public JBCheckboxMenuItem(String text, boolean b) {
    super(text, b);
  }

  public JBCheckboxMenuItem(String text, Icon icon, boolean b) {
    super(text, icon, b);
  }

  @Override
  public void paint(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    super.paint(g);
  }
}
