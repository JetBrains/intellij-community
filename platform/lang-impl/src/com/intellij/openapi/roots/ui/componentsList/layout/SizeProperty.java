package com.intellij.openapi.roots.ui.componentsList.layout;


import java.awt.*;

public interface SizeProperty {
  Dimension getSize(Component component);

  SizeProperty PREFERED_SIZE = new SizeProperty() {
    public Dimension getSize(Component component) {
      return component.getPreferredSize();
    }
  };

  SizeProperty MINIMUM_SIZE = new SizeProperty() {
    public Dimension getSize(Component component) {
      return component.getMinimumSize();
    }
  };

}
