package com.intellij.openapi.roots.ui.util;

import com.intellij.ui.SimpleColoredComponent;

public interface CellAppearance {
  void customize(SimpleColoredComponent component);
  String getText();
}
