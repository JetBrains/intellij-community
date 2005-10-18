package com.intellij.execution.junit2.segments;

import com.intellij.execution.ui.ConsoleViewContentType;

public interface InputConsumer {
  class DeafInputConsumer implements InputConsumer {
    public void onOutput(final String text, final ConsoleViewContentType contentType) {
    }
  }
  DeafInputConsumer DEAF = new DeafInputConsumer();
  void onOutput(String text, ConsoleViewContentType contentType);
}
