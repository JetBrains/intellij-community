package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;

public interface BufferingIndexStorage {

  interface BufferingStateListener {
    void bufferingStateChanged(boolean newState);

    void memoryStorageCleared();
  }

  void addBufferingStateListener(@NotNull BufferingStateListener listener);

  void removeBufferingStateListener(@NotNull BufferingStateListener listener);

  void setBufferingEnabled(boolean enabled);

  boolean isBufferingEnabled();

  void clearMemoryMap();

  void fireMemoryStorageCleared();
}