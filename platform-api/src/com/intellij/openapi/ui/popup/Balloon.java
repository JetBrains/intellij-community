package com.intellij.openapi.ui.popup;

import com.intellij.openapi.Disposable;
import com.intellij.ui.awt.RelativePoint;

import javax.swing.*;
import java.awt.*;

public interface Balloon extends Disposable {

  void show(RelativePoint target, Position prefferedPosition);

  void show(JLayeredPane pane);

  Dimension getPreferredSize();

  void setBounds(Rectangle bounds);

  void addListener(JBPopupListener listener);

  void hide();

  enum Position {
    below, above, atLeft, atRight
  }

}