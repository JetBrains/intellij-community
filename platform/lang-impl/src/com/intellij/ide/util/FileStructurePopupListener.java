package com.intellij.ide.util;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.ApiStatus;

import java.util.EventListener;

@ApiStatus.Internal
@ApiStatus.Experimental
public interface FileStructurePopupListener extends EventListener {
  @Topic.ProjectLevel
  Topic<FileStructurePopupListener> TOPIC = new Topic<>("file structure popup events", FileStructurePopupListener.class, Topic.BroadcastDirection.NONE);

  void stateChanged(boolean opened);
}
