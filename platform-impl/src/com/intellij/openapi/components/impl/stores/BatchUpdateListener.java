package com.intellij.openapi.components.impl.stores;

import com.intellij.util.messages.Topic;

public interface BatchUpdateListener {
  Topic<BatchUpdateListener> TOPIC = new Topic<BatchUpdateListener>("batch update listener", BatchUpdateListener.class);

  void onBatchUpdateStarted();
  void onBatchUpdateFinished();
}
