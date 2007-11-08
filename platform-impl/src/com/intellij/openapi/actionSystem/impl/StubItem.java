package com.intellij.openapi.actionSystem.impl;

import javax.swing.*;
import java.awt.*;

public class StubItem extends JMenuItem {
  public StubItem(){
    setEnabled(false);
  }

  public Dimension getPreferredSize() {
    return new Dimension();
  }
}
