package com.intellij.openapi.wm;

import javax.swing.*;
import java.util.EventListener;

public interface StatusBarCustomComponentFactory extends EventListener {
  JComponent createComponent(final StatusBar statusBar);

  void disposeComponent(StatusBar statusBar, final JComponent c);
}
