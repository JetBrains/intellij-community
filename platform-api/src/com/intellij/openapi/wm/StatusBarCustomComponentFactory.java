package com.intellij.openapi.wm;

import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.EventListener;

public abstract class StatusBarCustomComponentFactory<T extends JComponent> implements EventListener {
  public static final ExtensionPointName<StatusBarCustomComponentFactory> EP_NAME = ExtensionPointName.create("com.intellij.statusBarComponent");

  public abstract T createComponent(@NotNull final StatusBar statusBar);

  public void disposeComponent(@NotNull StatusBar statusBar, @NotNull final T c) {
  }
}
