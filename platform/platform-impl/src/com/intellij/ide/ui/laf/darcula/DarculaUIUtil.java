/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ide.ui.laf.darcula;

import com.intellij.util.ui.MacUIUtil;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DarculaUIUtil {
  public static final Color GLOW_COLOR = new Color(96, 175, 255);

  public static void paintFocusRing(Graphics g, int x, int y, int width, int height) {
    MacUIUtil.paintFocusRing((Graphics2D)g, GLOW_COLOR, new Rectangle(x, y, width, height));
  }

  public static void paintFocusOval(Graphics g, int x, int y, int width, int height) {
    MacUIUtil.paintFocusRing((Graphics2D)g, GLOW_COLOR, new Rectangle(x, y, width, height), true);
  }
}
