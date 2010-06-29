/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.util.ui.ButtonlessScrollBarUI;

import javax.swing.*;
import javax.swing.plaf.ScrollPaneUI;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public class JBScrollPane extends JScrollPane {
  public JBScrollPane(Component view) {
    super(view);
  }

  public JBScrollPane() {
  }

  public JBScrollPane(int vsbPolicy, int hsbPolicy) {
    super(vsbPolicy, hsbPolicy);
  }


  public JBScrollPane(Component view, int vsbPolicy, int hsbPolicy) {
    super(view, vsbPolicy, hsbPolicy);
  }

  /**
   * Scrollpane's background should be always in sync with view's background
   */
  public void setUI(ScrollPaneUI ui) {
    super.setUI(ui);
    // We need to set color of viewport later because UIManager
    // updates UI of scroll pane and only after that updates UI
    // of its children. To be the last in this sequence we need
    // set background later.
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        Component component = getViewport().getView();
        if (component != null) {
          getViewport().setBackground(component.getBackground());
        }
      }
    });

    getVerticalScrollBar().setUI(ButtonlessScrollBarUI.createNormal());
    getHorizontalScrollBar().setUI(ButtonlessScrollBarUI.createNormal());
  }

}
