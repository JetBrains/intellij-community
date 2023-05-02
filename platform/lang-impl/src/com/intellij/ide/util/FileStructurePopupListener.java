package com.intellij.ide.util;

import com.intellij.util.messages.Topic;
import java.util.EventListener;

public interface FileStructurePopupListener extends EventListener {
  @Topic.ProjectLevel
  Topic<FileStructurePopupListener> TOPIC = new Topic<>("file structure popup events", FileStructurePopupListener.class, Topic.BroadcastDirection.NONE);

  void stateChanged(boolean opened);
}
