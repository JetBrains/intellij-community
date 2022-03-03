// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.util.messages.Topic;

/**
 * Listener for receiving notifications when the IDE window is activated or deactivated.
 */
public interface FrameStateListener {
  @Topic.AppLevel
  Topic<FrameStateListener> TOPIC = new Topic<>("FrameStateListener", FrameStateListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  /**
   * Called when the IDE window is deactivated.
   */
  default void onFrameDeactivated() {
  }

  /**
   * Called when the IDE window is activated.
   */
  default void onFrameActivated() {
  }
}
