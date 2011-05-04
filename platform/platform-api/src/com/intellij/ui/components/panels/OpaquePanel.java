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
public class OpaquePanel extends JPanel {
  private boolean myOpaqueActive = true;

  public OpaquePanel() {
    this(null, null);
  }

  public OpaquePanel(LayoutManager layout) {
    this(layout, null);
  }

  public OpaquePanel(Color color) {
    this(null, color);
  }

  public OpaquePanel(LayoutManager layoutManager, Color color) {
    super(layoutManager);
    setBackground(color);
  }

  protected void paintComponent(Graphics g) {
    if (isOpaqueActive()) {
      final Color bg = getBackground();
      g.setColor(bg);
      final Dimension size = getSize();
      g.fillRect(0, 0, size.width, size.height);
    }
  }

  public boolean isOpaqueActive() {
    return myOpaqueActive;
  }

  public void setOpaqueActive(final boolean opaqueActive) {
    myOpaqueActive = opaqueActive;
  }

  public static class List extends OpaquePanel {
    public List() {
    }

    public List(LayoutManager layout) {
      super(layout);
    }

    public List(Color color) {
      super(color);
    }

    public List(LayoutManager layoutManager, Color color) {
      super(layoutManager, color);
    }
  }
}
