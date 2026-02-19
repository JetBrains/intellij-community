// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

/**
 * Listener for receiving notifications when the IDE window is activated or deactivated.
 * <p>
 * Please note that a 'spurious' sequence of events can be generated sometimes - a 'deactivated' event followed by 'activated' event
 * (for the same frame). E.g. this happens on Linux when a dialog window closes - due to an asynchronous nature of focus transfers on Linux,
 * this is perceived by AWT, as the dialog window losing focus (to nowhere), followed by the main frame getting focus.
 */
public interface FrameStateListener {
  @Topic.AppLevel
  Topic<FrameStateListener> TOPIC = new Topic<>("FrameStateListener", FrameStateListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN);

  /**
   * Called when the IDE window is deactivated (some other application receives focus).
   *
   * @deprecated {@link ApplicationActivationListener#applicationDeactivated(IdeFrame)} can be used as an equivalent replacement. Use
   * {@link #onFrameDeactivated(IdeFrame)}, if switching between different project windows also needs to be tracked.
   */
  @Deprecated
  default void onFrameDeactivated() {
  }

  /**
   * Called when the IDE window is activated (it gets focus instead of some other application).
   *
   * @deprecated {@link ApplicationActivationListener#applicationActivated(IdeFrame)} can be used as an equivalent replacement. Use
   * {@link #onFrameActivated(IdeFrame)}, if switching between different project windows also needs to be tracked.
   */
  @Deprecated
  default void onFrameActivated() {
  }

  /**
   * Invoked when an IDE frame becomes active, i.e. either it itself or one of its child windows becomes focused. This can happen
   * on IDE activation, or e.g. on switching between different IDE projects' windows.
   */
  default void onFrameActivated(@NotNull IdeFrame frame) {}

  /**
   * Invoked when an IDE frame becomes inactive, i.e. neither it itself nor one of its child windows is focused anymore. This can happen
   * on IDE deactivation, or e.g. on switching between different IDE projects' windows.
   */
  default void onFrameDeactivated(@NotNull IdeFrame frame) {}
}
