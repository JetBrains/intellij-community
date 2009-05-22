package com.intellij.openapi.wm;

import java.awt.event.KeyEvent;
import java.util.List;

public interface KeyEventProcessor {

  Boolean dispatch(KeyEvent e, Context context);
  void finish(Context context);

  interface Context {
    List<KeyEvent> getQueue();
    void dispatch(List<KeyEvent> events);
  }

}