// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  /**
   * @deprecated use {@link FrameStateListener} directly
   */
  @Deprecated
  abstract class Adapter implements FrameStateListener {
  }
}
