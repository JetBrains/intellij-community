package com.intellij.openapi.editor.impl.event;

import java.util.EventListener;

public interface MarkupModelListener extends EventListener {
  MarkupModelListener[] EMPTY_ARRAY = new MarkupModelListener[0];
  void rangeHighlighterChanged(MarkupModelEvent event);
}
