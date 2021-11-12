// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * If you are interested in listening UI changes you have to
 * use this listener instead of registering {@code PropertyChangeListener}
 * into {@code UIManager}
 *
 * @author Vladimir Kondratyev
 */
public interface LafManagerListener extends EventListener {
  @Topic.AppLevel
  Topic<LafManagerListener> TOPIC = new Topic<>(LafManagerListener.class, Topic.BroadcastDirection.NONE, true);

  void lookAndFeelChanged(@NotNull LafManager source);
}
