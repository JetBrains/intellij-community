package com.intellij.openapi.wm.impl;

import com.intellij.openapi.wm.IdeGlassPane;

import java.awt.*;

/**
 * @author spleaner
 */
public interface IdeGlassPaneEx extends IdeGlassPane {
  Component add(final Component comp);
  void remove(final Component comp);
}
