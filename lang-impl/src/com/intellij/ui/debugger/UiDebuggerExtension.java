package com.intellij.ui.debugger;

import com.intellij.openapi.Disposable;

import javax.swing.*;

public interface UiDebuggerExtension extends Disposable {

  JComponent getComponent();
  String getName();

}