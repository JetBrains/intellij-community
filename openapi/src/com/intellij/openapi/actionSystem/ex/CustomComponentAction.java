package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.actionSystem.Presentation;

import javax.swing.*;

public interface CustomComponentAction {
  /**
   * @return custom JComponent that represents action in UI.
   * You (as a client/implementor) or this interface do not allow to invoke
   * this method directly. Only action system can invoke it!
   */
  JComponent createCustomComponent(Presentation presentation);
}
