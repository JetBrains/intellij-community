// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.util.messages.Topic;

/**
 * Listener for receiving notifications when the IDEA window is activated or deactivated.
 *
 * @since 5.0.2
 */
public interface FrameStateListener {
  Topic<FrameStateListener> TOPIC = new Topic<>("FrameStateListener", FrameStateListener.class);

  /**
   * Called when the IDEA window is deactivated.
   */
  default void onFrameDeactivated() {
  }

  /**
   * Called when the IDEA window is activated.
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
