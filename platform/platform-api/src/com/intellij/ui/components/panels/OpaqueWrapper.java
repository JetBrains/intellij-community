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
package com.intellij.ui.components.panels;


import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Belyaev
 */
public class OpaqueWrapper extends Wrapper {

  public OpaqueWrapper(JComponent wrapped, Color color) {
    super(wrapped);
    setBackground(color);
  }

  public OpaqueWrapper(LayoutManager layoutManager, JComponent wrapped, Color color) {
    super(layoutManager, wrapped);
    setBackground(color);
  }

  protected void paintComponent(Graphics g) {
    g.setColor(getBackground());
    Dimension size = getSize();
    g.fillRect(0, 0, size.width, size.height);
  }
}
