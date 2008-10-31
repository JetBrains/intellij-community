package com.intellij.openapi.ui.popup;

import com.intellij.openapi.Disposable;
import com.intellij.ui.awt.RelativePoint;

public interface Balloon extends Disposable {

  void show(RelativePoint target, Position prefferedPosition);

  enum Position {
    below, above, atLeft, atRight
  }

}