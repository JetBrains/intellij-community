package com.intellij.ui.debugger;

import javax.swing.*;

public interface UiDebuggerExtension {

  JComponent getComponent();
  String getName();

  void disposeUiResources();

}