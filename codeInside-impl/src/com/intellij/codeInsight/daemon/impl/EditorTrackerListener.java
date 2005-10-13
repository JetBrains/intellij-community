package com.intellij.codeInsight.daemon.impl;

import com.intellij.openapi.editor.Editor;

import java.util.EventListener;

public interface EditorTrackerListener extends EventListener{
  void activeEditorsChanged(final Editor[] editors);
}
