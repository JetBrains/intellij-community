// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * If you are interested in listening UI changes you have to use this listener instead of registering {@code PropertyChangeListener}
 * into {@code UIManager}.
 */
public interface LafManagerListener extends EventListener {
  @Topic.AppLevel
  Topic<LafManagerListener> TOPIC = new Topic<>(LafManagerListener.class, Topic.BroadcastDirection.NONE, true);

  /**
   * Not executed on an initial LaF set.
   */
  void lookAndFeelChanged(@NotNull LafManager source);
}
