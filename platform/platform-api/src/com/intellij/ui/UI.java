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
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author max
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class UI {
  private static final Map<String, Color> ourColors = new HashMap<>();

  private UI() {
  }

  static {
    ourColors.put("panel.border.color", new Color(102, 101, 84));
    ourColors.put("panel.separator.color", new Color(180, 179, 169));

    ourColors.put("panel.custom.background", new Color(250, 249, 245));

    ourColors.put("link.foreground", new Color(82, 99, 155));
    ourColors.put("link.pressed.foreground", new JBColor(new Color(240, 0, 0), new Color(186, 111, 37)));
    ourColors.put("link.visited.foreground", new JBColor(new Color(128, 0, 128), new Color(151, 118, 169)));

    ourColors.put("bar.separator.foreground", getColor("panel.separator.color"));
    ourColors.put("bar.selected.separator.foreground", new Color(232, 231, 228));
    ourColors.put("bar.background", UIUtil.getPanelBackground());
    ourColors.put("bar.hover.background", UIUtil.getTreeSelectionBackground());
    ourColors.put("bar.selected.background", getColor("panel.custom.background"));
    ourColors.put("bar.hover.frame.foreground", UIUtil.getTreeSelectionBackground().darker());

    ourColors.put("popup.selected.background", UIUtil.getTreeSelectionBackground());
    ourColors.put("popup.selected.frame.foreground", UIUtil.getTreeSelectionBackground().darker());
    ourColors.put("popup.separator.foreground", JBColor.GRAY);

    ourColors.put("callout.background", JBColor.WHITE);
    ourColors.put("callout.frame.color", JBColor.RED);

    ourColors.put("underline.error", JBColor.RED);
    ourColors.put("underline.warning", JBColor.YELLOW);

    ourColors.put("tooltip.error", JBColor.RED);
    ourColors.put("tooltip.warning", JBColor.YELLOW.darker());

    ourColors.put("toolbar.background", UIUtil.getPanelBackground());
    ourColors.put("toolbar.hover.background", UIUtil.getTreeSelectionBackground());
    ourColors.put("toolbar.selected.background", getColor("panel.custom.background"));
    ourColors.put("toolbar.hover.frame.foreground", UIUtil.getTreeSelectionBackground().darker());

    ourColors.put("speedsearch.background", new Color(244, 249, 181));
    ourColors.put("speedsearch.foreground", JBColor.BLACK);
  }

  public static Color getColor(@NonNls String id) {
    if (UIUtil.isUnderDarcula()) {
      final Color color = UIManager.getColor(id);
      if (color != null) {
        return color;
      }
    }
    return ourColors.get(id);
  }
}
