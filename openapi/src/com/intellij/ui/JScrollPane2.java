/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.ui;

import javax.swing.*;
import javax.swing.plaf.ScrollPaneUI;
import java.awt.*;

/**
 * @author Vladimir Kondratyev
 */
public class JScrollPane2 extends JScrollPane {
  JScrollPane2(JComponent view) {
    super(view);
  }

  protected JScrollPane2() {
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
  }

}
