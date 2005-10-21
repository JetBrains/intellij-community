package com.intellij.openapi.actionSystem.ex;

import com.intellij.openapi.application.ModalityState;

public interface TimerListener {
  ModalityState getModalityState();
  void run();
}
