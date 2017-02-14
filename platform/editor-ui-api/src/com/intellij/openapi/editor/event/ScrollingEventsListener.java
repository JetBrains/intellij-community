package com.intellij.openapi.editor.event;


import com.intellij.openapi.editor.LogicalPosition;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface ScrollingEventsListener extends EventListener {
  void scrollToCaret();
  void scrollTo(@NotNull LogicalPosition pos);
}
