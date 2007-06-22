package com.intellij.history.integration.stubs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

public class StubCommandProcessor extends CommandProcessor {
  public void executeCommand(Runnable runnable, @Nls String name, Object groupId) {
    throw new UnsupportedOperationException();
  }

  public void executeCommand(Project project, Runnable runnable, @Nls String name, Object groupId) {
    throw new UnsupportedOperationException();
  }

  public void executeCommand(Project project,
                             Runnable runnable,
                             @Nls String name,
                             Object groupId,
                             UndoConfirmationPolicy undoConfirmationPolicy) {
    throw new UnsupportedOperationException();
  }

  public void setCurrentCommandName(@Nls String name) {
    throw new UnsupportedOperationException();
  }

  public void setCurrentCommandGroupId(Object groupId) {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public Runnable getCurrentCommand() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public String getCurrentCommandName() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public Object getCurrentCommandGroupId() {
    throw new UnsupportedOperationException();
  }

  @Nullable
  public Project getCurrentCommandProject() {
    throw new UnsupportedOperationException();
  }

  public void addCommandListener(CommandListener listener) {
    throw new UnsupportedOperationException();
  }

  public void addCommandListener(CommandListener listener, Disposable parentDisposable) {
    throw new UnsupportedOperationException();
  }

  public void removeCommandListener(CommandListener listener) {
    throw new UnsupportedOperationException();
  }

  public void runUndoTransparentAction(Runnable action) {
    throw new UnsupportedOperationException();
  }

  public boolean isUndoTransparentActionInProgress() {
    throw new UnsupportedOperationException();
  }

  public void markCurrentCommandAsComplex(Project project) {
    throw new UnsupportedOperationException();
  }
}
