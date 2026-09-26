// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;


final class CommandListeners implements CommandListener {
  private final List<CommandListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  void addCommandListener(@NotNull CommandListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void commandStarted(@NotNull CommandEvent event) {
    for (CommandListener listener : myListeners) {
      try {
        listener.commandStarted(event);
      }
      catch (Throwable e) {
        CoreCommandProcessor.LOG.error(e);
      }
    }
  }

  @Override
  public void beforeCommandFinished(@NotNull CommandEvent event) {
    for (CommandListener listener : myListeners) {
      try {
        listener.beforeCommandFinished(event);
      }
      catch (Throwable e) {
        CoreCommandProcessor.LOG.error(e);
      }
    }
  }

  @Override
  public void commandFinished(@NotNull CommandEvent event) {
    for (CommandListener listener : myListeners) {
      try {
        listener.commandFinished(event);
      }
      catch (Throwable e) {
        CoreCommandProcessor.LOG.error(e);
      }
    }
  }

  @Override
  public void undoTransparentActionStarted() {
    for (CommandListener listener : myListeners) {
      try {
        listener.undoTransparentActionStarted();
      }
      catch (Throwable e) {
        CoreCommandProcessor.LOG.error(e);
      }
    }
  }

  @Override
  public void beforeUndoTransparentActionFinished() {
    for (CommandListener listener : myListeners) {
      try {
        listener.beforeUndoTransparentActionFinished();
      }
      catch (Throwable e) {
        CoreCommandProcessor.LOG.error(e);
      }
    }
  }

  @Override
  public void undoTransparentActionFinished() {
    for (CommandListener listener : myListeners) {
      try {
        listener.undoTransparentActionFinished();
      }
      catch (Throwable e) {
        CoreCommandProcessor.LOG.error(e);
      }
    }
  }
}
