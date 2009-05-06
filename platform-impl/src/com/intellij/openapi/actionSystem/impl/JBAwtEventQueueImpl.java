package com.intellij.openapi.actionSystem.impl;

import com.intellij.openapi.actionSystem.JBAwtEventQueue;
import com.intellij.ide.IdeEventQueue;

import java.awt.event.MouseEvent;

public class JBAwtEventQueueImpl extends JBAwtEventQueue {
  public void blockNextEvents(MouseEvent e) {
    IdeEventQueue.getInstance().blockNextEvents(e);
  }
}
