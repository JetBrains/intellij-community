package com.intellij.ui.content;

import com.intellij.ui.content.ContentManagerEvent;

import java.util.EventListener;

public interface ContentManagerListener extends EventListener{
  void contentAdded(ContentManagerEvent event);
  void contentRemoved(ContentManagerEvent event);
  void contentRemoveQuery(ContentManagerEvent event);
  void selectionChanged(ContentManagerEvent event);
}