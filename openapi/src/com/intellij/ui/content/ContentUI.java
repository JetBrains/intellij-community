
package com.intellij.ui.content;

import javax.swing.*;

public interface ContentUI {
  JComponent getComponent();
  void setManager(ContentManager manager);
}
