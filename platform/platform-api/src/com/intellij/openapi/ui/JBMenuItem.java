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
 * @author ignatov
 */
public class JBMenuItem extends JMenuItem {
  public JBMenuItem(String message) {
    super(message);
  }

  public JBMenuItem(String text, Icon icon) {
    super(text, icon);
  }

  public JBMenuItem(Action a) {
    super(a);
  }

  @Override
  public void paint(Graphics g) {
    GraphicsUtil.setupAntialiasing(g);
    super.paint(g);
  }
}
