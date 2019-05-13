// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

public interface CommandListener extends EventListener {
  Topic<CommandListener> TOPIC = new Topic<>("command events", CommandListener.class);

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