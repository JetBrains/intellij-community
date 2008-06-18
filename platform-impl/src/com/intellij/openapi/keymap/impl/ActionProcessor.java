package com.intellij.openapi.keymap.impl;

import com.intellij.openapi.actionSystem.*;

import java.awt.event.InputEvent;

public interface ActionProcessor {

  AnActionEvent createEvent(InputEvent inputEvent, DataContext context, String place, Presentation presentation, ActionManager manager);

  void onUpdatePassed(final InputEvent inputEvent, final AnAction action, final AnActionEvent actionEvent);

  void performAction(final InputEvent e, final AnAction action, final AnActionEvent actionEvent);
}