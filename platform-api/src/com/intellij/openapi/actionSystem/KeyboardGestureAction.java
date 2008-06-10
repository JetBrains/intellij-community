package com.intellij.openapi.actionSystem;

public interface KeyboardGestureAction {
  enum State {
    init, action, finsh
  }

  enum Type {
    dblClick, hold
  }
}