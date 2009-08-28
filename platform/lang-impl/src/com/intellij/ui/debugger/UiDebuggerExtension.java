package com.intellij.ui.debugger;

import com.intellij.openapi.extensions.ExtensionPointName;

import javax.swing.*;

public interface UiDebuggerExtension {
  ExtensionPointName<UiDebuggerExtension> EP_NAME = ExtensionPointName.create("com.intellij.uiDebuggerExtension");

  JComponent getComponent();
  String getName();

  void disposeUiResources();

}