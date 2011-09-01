package com.intellij.ui;

import javax.swing.*;

/**
 * @author evgeny.zakrevsky
 */

public interface ComponentWithAnchor {
  JComponent getAnchor();
  void setAnchor(JComponent anchor);
}
