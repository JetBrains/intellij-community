// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;


import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;


final class CommandPublisher implements CommandListener {
  private final CommandListeners listeners;
  private final CommandListener publisher;

  CommandPublisher() {
    CommandListeners listeners = new CommandListeners();
    MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.simpleConnect().subscribe(TOPIC, listeners);
    this.listeners = listeners;
    this.publisher = messageBus.syncPublisher(TOPIC);
  }

  void addCommandListener(@NotNull CommandListener listener) {
    listeners.addCommandListener(listener);
  }

  @Override
  public void commandStarted(@NotNull CommandEvent event) {
    publisher.commandStarted(event);
  }

  @Override
  public void beforeCommandFinished(@NotNull CommandEvent event) {
    publisher.beforeCommandFinished(event);
  }

  @Override
  public void commandFinished(@NotNull CommandEvent event) {
    publisher.commandFinished(event);
  }

  @Override
  public void undoTransparentActionStarted() {
    publisher.undoTransparentActionStarted();
  }

  @Override
  public void beforeUndoTransparentActionFinished() {
    publisher.beforeUndoTransparentActionFinished();
  }

  @Override
  public void undoTransparentActionFinished() {
    publisher.undoTransparentActionFinished();
  }
}
