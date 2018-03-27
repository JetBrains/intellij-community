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
package com.intellij.ui.roots;

import com.intellij.ui.ClickListener;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Eugene Zhuravlev
 * @author 2003
 */
public class IconActionComponent extends ScalableIconComponent {
  public IconActionComponent(Icon icon, Icon rolloverIcon, String tooltipText, final Runnable action) {
    super(icon, rolloverIcon);
    this.addMouseListener(new MouseAdapter() {
      public void mouseEntered(MouseEvent e) {
        setSelected(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      }

      public void mouseExited(MouseEvent e) {
        setSelected(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      }
    });
    new ClickListener() {
      @Override
      public boolean onClick(@NotNull MouseEvent e, int clickCount) {
        if (action != null) {
          action.run();
          return true;
        }
        return false;
      }
    }.installOn(this);

    if (tooltipText != null) {
      this.setToolTipText(tooltipText);
    }
  }

}
