package com.intellij.openapi.actionSystem;

import com.intellij.openapi.application.ModalityState;

public interface TimerListener {
  ModalityState getModalityState();
  void run();
}
