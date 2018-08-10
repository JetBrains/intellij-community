// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.command;

import com.intellij.util.messages.Topic;

import java.util.EventListener;

public interface CommandListener extends EventListener {
  Topic<CommandListener> TOPIC = new Topic<>("command events", CommandListener.class);

  default void commandStarted(CommandEvent event) {
  }

  default void beforeCommandFinished(CommandEvent event) {
  }

  default void commandFinished(CommandEvent event) {
  }

  default void undoTransparentActionStarted() {
  }

  default void beforeUndoTransparentActionFinished() {
  }

  default void undoTransparentActionFinished() {
  }
}