package com.intellij.execution.ui;

import com.intellij.openapi.Disposable;

import java.util.Collection;

public interface ObservableConsoleView extends ConsoleView {

  void addChangeListener(ChangeListener listener, Disposable parent);

  interface ChangeListener {
    void contentAdded(Collection<ConsoleViewContentType> types);
  }

}
