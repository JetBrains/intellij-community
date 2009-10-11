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

/*
 * Class MultiLineTooltipUI
 * @author Jeka
 */
package com.intellij.ui;

import com.intellij.openapi.util.text.LineTokenizer;

import javax.swing.*;
import javax.swing.plaf.metal.MetalToolTipUI;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MultiLineTooltipUI extends MetalToolTipUI {
  private final List myLines = new ArrayList();

  public void paint(Graphics g, JComponent c) {
    FontMetrics metrics = g.getFontMetrics(g.getFont());
    Dimension size = c.getSize();
    g.setColor(c.getBackground());
    g.fillRect(0, 0, size.width, size.height);
    g.setColor(c.getForeground());
    int idx = 0;
    for (Iterator it = myLines.iterator(); it.hasNext(); idx++) {
      String line = (String)it.next();
      g.drawString(line, 3, (metrics.getHeight()) * (idx + 1));
    }
  }

  public Dimension getPreferredSize(JComponent c) {
    FontMetrics metrics = c.getFontMetrics(c.getFont());
    String tipText = ((JToolTip)c).getTipText();
    if (tipText == null) {
      tipText = "";
    }
    int maxWidth = 0;
    myLines.clear();

    final String[] lines = LineTokenizer.tokenize(tipText.toCharArray(), false);
    for (String line : lines) {
      myLines.add(line);
      int width = SwingUtilities.computeStringWidth(metrics, line);
      if (width > maxWidth) {
        maxWidth = width;
      }
    }

    int height = metrics.getHeight() * ((lines.length < 1)? 1 : lines.length);
    return new Dimension(maxWidth + 6, height + 4);
  }
}
