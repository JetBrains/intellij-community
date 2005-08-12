/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Feb 17, 2005
 * Time: 4:22:50 PM
 * To change this template use File | Settings | File Templates.
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class UI {
  private static Map<String, Color> ourColors = new HashMap<String, Color>();

  static {
    ourColors.put("panel.border.color", new Color(102, 101, 84));
    ourColors.put("panel.separator.color", new Color(180, 179, 169));

    ourColors.put("panel.custom.background", new Color(250, 249, 245));

    ourColors.put("link.foreground", new Color(82, 99, 155));
    ourColors.put("link.pressed.foreground", new Color(240, 0, 0));
    ourColors.put("link.visited.foreground", new Color(128, 0, 128));

    ourColors.put("bar.separator.foreground", UI.getColor("panel.separator.color"));
    ourColors.put("bar.selected.separator.foreground", new Color(232, 231, 228));
    ourColors.put("bar.background", UIManager.getColor("Panel.background"));
    ourColors.put("bar.hover.background", UIManager.getColor("Tree.selectionBackground"));
    ourColors.put("bar.selected.background", UI.getColor("panel.custom.background"));
    ourColors.put("bar.hover.frame.foreground", UIManager.getColor("Tree.selectionBackground").darker());

    ourColors.put("popup.selected.background", UIManager.getColor("Tree.selectionBackground"));
    ourColors.put("popup.selected.frame.foreground", UIManager.getColor("Tree.selectionBackground").darker());
    ourColors.put("popup.separator.foreground", Color.gray);

    ourColors.put("callout.background", Color.white);
    ourColors.put("callout.frame.color", Color.red);

    ourColors.put("underline.error", Color.red);
    ourColors.put("underline.warning", Color.yellow);

    ourColors.put("tooltip.error", Color.red);
    ourColors.put("tooltip.warning", Color.yellow.darker());

    ourColors.put("toolbar.background", UIManager.getColor("Panel.background"));
    ourColors.put("toolbar.hover.background", UIManager.getColor("Tree.selectionBackground"));
    ourColors.put("toolbar.selected.background", UI.getColor("panel.custom.background"));
    ourColors.put("toolbar.hover.frame.foreground", UIManager.getColor("Tree.selectionBackground").darker());

    ourColors.put("speedsearch.background", new Color(244, 249, 181));
    ourColors.put("speedsearch.foreground", Color.black);
  }

  public static Color getColor(@NonNls String id) {
    return ourColors.get(id);
  }
}
