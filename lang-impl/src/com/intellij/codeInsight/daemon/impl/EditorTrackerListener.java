package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;

import java.util.EventListener;
import java.util.List;

public interface EditorTrackerListener extends EventListener{
  void activeEditorsChanged(List<Editor> editors);
}
