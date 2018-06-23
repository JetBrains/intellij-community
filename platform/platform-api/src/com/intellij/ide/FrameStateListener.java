// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

/**
 * Listener for receiving notifications when the IDEA window is activated or deactivated.
 *
 * @since 5.0.2
 * @see FrameStateManager#addListener(FrameStateListener)
 * @see FrameStateManager#removeListener(FrameStateListener)
 */
public interface FrameStateListener {
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
