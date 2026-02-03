// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface CommandListener extends EventListener {
  // immediateDelivery
  @Topic.AppLevel
  Topic<CommandListener> TOPIC = new Topic<>(CommandListener.class, Topic.BroadcastDirection.TO_DIRECT_CHILDREN, true);

  default void commandStarted(@NotNull CommandEvent event) {
  }

  default void beforeCommandFinished(@NotNull CommandEvent event) {
  }

  default void commandFinished(@NotNull CommandEvent event) {
  }

  default void undoTransparentActionStarted() {
  }

  default void beforeUndoTransparentActionFinished() {
  }

  default void undoTransparentActionFinished() {
  }
}