// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.impl;

import com.intellij.util.messages.Topic;

import java.util.EventListener;

/**
 * Receives lifecycle events for Java debugger sessions.
 * <p>
 * A session is one logical debugging operation. One session can attach to more than one target VM.
 * <p>
 * A usual session produces these events:
 * <pre>{@code
 * sessionCreated
 * sessionAttached
 * sessionDetached
 * sessionRemoved
 * }</pre>
 * <p>
 * A session with one reattach produces these events:
 * <pre>{@code
 * sessionCreated
 * sessionAttached
 * sessionDetached
 * sessionAttached
 * sessionDetached
 * sessionRemoved
 * }</pre>
 * <p>
 * {@link #sessionCreated(DebuggerSession)} and {@link #sessionRemoved(DebuggerSession)} occur once for a session.
 * The attach and detach events can occur multiple times.
 * A failed attach can omit the attach and detach events.
 * <p>
 * The manager might remove a session without a preceding detach event.
 * Use {@link #sessionRemoved(DebuggerSession)} for final cleanup.
 */
public interface DebuggerManagerListener extends EventListener {

  @Topic.ProjectLevel
  Topic<DebuggerManagerListener> TOPIC =
    new Topic<>("DebuggerManagerListener", DebuggerManagerListener.class, Topic.BroadcastDirection.NONE);

  /**
   * The manager calls this method after it creates and registers a session.
   * <p>
   * This event occurs once. It does not mean that a target VM is attached.
   */
  default void sessionCreated(DebuggerSession session) {
  }

  /**
   * The manager calls this method after the session attaches to a target VM.
   * <p>
   * This event occurs for the initial attach and for each successful reattach.
   * Create state that belongs to one VM connection here.
   */
  default void sessionAttached(DebuggerSession session) {
  }

  /**
   * The manager calls this method after the session detaches from a target VM.
   * <p>
   * The session can attach again after this event.
   * Release state for the current VM connection here.
   * Keep session state until {@link #sessionRemoved(DebuggerSession)}.
   */
  default void sessionDetached(DebuggerSession session) {
  }

  /**
   * The manager calls this method after it removes a disposed session.
   * <p>
   * This event occurs once and ends the session lifecycle.
   * Release all remaining session and VM connection state here.
   */
  default void sessionRemoved(DebuggerSession session) {
  }
}
