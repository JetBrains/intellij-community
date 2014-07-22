/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.profile.codeInspection.ui.inspectionsTree;


import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.ui.JBColor;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * @author Dmitry Batkovich
 */
public class MultiScopeSeverityIcon implements Icon {
  private final static JBColor MIXED_SEVERITY_COLOR = JBColor.DARK_GRAY;

  private final static int SIZE = 12;

  private final LinkedHashMap<String, HighlightSeverity> myScopeToAverageSeverityMap;

  public MultiScopeSeverityIcon(final LinkedHashMap<String, HighlightSeverity> scopeToAverageSeverityMap) {
    myScopeToAverageSeverityMap = scopeToAverageSeverityMap;
  }

  public LinkedHashMap<String, HighlightSeverity> getScopeToAverageSeverityMap() {
    return myScopeToAverageSeverityMap;
  }

  @Override
  public void paintIcon(final Component c, final Graphics g, final int i, final int j) {
    final int iconWidth = getIconWidth();

    final int partWidth = iconWidth / myScopeToAverageSeverityMap.size();

    final Collection<HighlightSeverity> values = myScopeToAverageSeverityMap.values();
    int idx = 0;
    for (final HighlightSeverity severity : values) {
      final Icon icon = HighlightDisplayLevel.find(severity).getIcon();
      g.setColor(icon instanceof HighlightDisplayLevel.SingleColorIconWithMask ?
                 ((HighlightDisplayLevel.SingleColorIconWithMask)icon).getColor() : MIXED_SEVERITY_COLOR);
      final int x = i + partWidth * idx;
      g.fillRect(x, j, partWidth, getIconHeight());
      idx++;
    }
    g.drawImage(HighlightDisplayLevel.ImageHolder.ourErrorMaskImage, i, j, null);
  }

  @Override
  public int getIconWidth() {
    return SIZE;
  }

  @Override
  public int getIconHeight() {
    return SIZE;
  }
}
