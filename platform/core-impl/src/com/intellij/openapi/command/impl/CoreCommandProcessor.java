// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class CoreCommandProcessor extends CommandProcessorEx {

  @ApiStatus.Internal
  protected static final Logger LOG = Logger.getInstance("#com.intellij.openapi.command.impl");

  private final CommandPublisher eventPublisher = new CommandPublisher();
  private final Stack<@Nullable CommandDescriptor> interruptedCommands = new Stack<>();
  private @Nullable CommandDescriptor currentCommand;
  private int undoTransparentCount;
  private int allowMergeGlobalCommandsCount = 0;

  @Override
  public void executeCommand(
    @Nullable Project project,
    @NotNull Runnable runnable,
    @Nullable String name,
    @Nullable Object groupId
  ) {
    executeCommand(project, runnable, name, groupId, UndoConfirmationPolicy.DEFAULT);
  }

  @Override
  public void executeCommand(
    @Nullable Project project,
    @NotNull Runnable runnable,
    @Nullable String name,
    @Nullable Object groupId,
    @Nullable Document document
  ) {
    executeCommand(project, runnable, name, groupId, UndoConfirmationPolicy.DEFAULT, document);
  }

  @Override
  public void executeCommand(
    @Nullable Project project,
    @NotNull Runnable command,
    @Nullable String name,
    @Nullable Object groupId,
    @NotNull UndoConfirmationPolicy undoConfirmationPolicy
  ) {
    executeCommand(project, command, name, groupId, undoConfirmationPolicy, null);
  }

  @Override
  public void executeCommand(
    @Nullable Project project,
    @NotNull Runnable command,
    @Nullable String name,
    @Nullable Object groupId,
    @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
    @Nullable Document document
  ) {
    executeCommand(project, command, name, groupId, undoConfirmationPolicy, true, document);
  }

  @Override
  public void executeCommand(
    @Nullable Project project,
    @NotNull Runnable command,
    @Nullable String name,
    @Nullable Object groupId,
    @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
    boolean shouldRecordCommandForActiveDocument
  ) {
    executeCommand(project, command, name, groupId, undoConfirmationPolicy, shouldRecordCommandForActiveDocument, null);
  }

  @Override
  public void executeCommand(
    @Nullable Project project,
    @NotNull Runnable command,
    @Nullable @NlsContexts.Command String name,
    @Nullable Object groupId,
    @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
    boolean shouldRecordCommandForActiveDocument,
    @Nullable Document document
  ) {
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();

    if (LOG.isDebugEnabled()) {
      CommandDescriptor currentCommand = this.currentCommand;
      LOG.debug(String.format(
        "executeCommand: %s, name = %s, groupId = %s, in command = %s, in transparent action = %s",
        command,
        name,
        groupId,
        currentCommand == null ? "<null>" : currentCommand.getName(),
        isUndoTransparentActionInProgress()
      ));
    }

    if (project != null && project.isDisposed()) {
      LOG.error("Project " + project + " already disposed");
      return;
    }

    if (currentCommand != null) {
      application.runWriteIntentReadAction(() -> {
        command.run();
        return null;
      });
      return;
    }

    CommandDescriptor descriptor = new CommandDescriptor(
      command,
      project,
      name,
      groupId,
      undoConfirmationPolicy,
      shouldRecordCommandForActiveDocument,
      document
    );
    currentCommand = descriptor;
    application.runWriteIntentReadAction(() -> {
      Throwable throwable = null;
      try {
        fireCommandStarted(descriptor);
        command.run();
      }
      catch (Throwable th) {
        throwable = th;
      }
      finally {
        Throwable finalThrowable = throwable;
        ProgressManager.getInstance().executeNonCancelableSection(() -> {
          finishCommand(descriptor, finalThrowable);
        });
        if (finalThrowable instanceof ProcessCanceledException) {
          throw (ProcessCanceledException)finalThrowable;
        }
      }
      return null;
    });
  }

  @Override
  public @Nullable CommandToken startCommand(
    @Nullable Project project,
    @Nullable String name,
    @Nullable Object groupId,
    @NotNull UndoConfirmationPolicy undoConfirmationPolicy
  ) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    if (project != null && project.isDisposed()) {
      return null;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("startCommand: name = " + name + ", groupId = " + groupId);
    }

    if (currentCommand != null) {
      return null;
    }

    CommandDescriptor descriptor = new CommandDescriptor(
      EmptyRunnable.INSTANCE,
      project,
      name,
      groupId,
      undoConfirmationPolicy,
      true,
      getDocumentFromGroupId(groupId)
    );
    currentCommand = descriptor;
    fireCommandStarted(descriptor);
    return descriptor;
  }

  @Override
  public void finishCommand(@NotNull CommandToken command, @Nullable Throwable throwable) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    CommandDescriptor currentCommand = this.currentCommand;
    LOG.assertTrue(currentCommand != null, "no current command in progress");
    fireCommandFinished(currentCommand);
  }

  @Override
  public void enterModal() {
    ThreadingAssertions.assertEventDispatchThread();
    CommandDescriptor currentCommand = this.currentCommand;
    interruptedCommands.push(currentCommand);
    if (currentCommand != null) {
      fireCommandFinished(currentCommand);
    }
  }

  @Override
  public void leaveModal() {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.assertTrue(currentCommand == null, "Command must not run: " + currentCommand);
    CommandDescriptor descriptor = interruptedCommands.pop();
    currentCommand = descriptor;
    if (descriptor != null) {
      fireCommandStarted(descriptor);
    }
  }

  @Override
  public void setCurrentCommandName(String name) {
    ThreadingAssertions.assertWriteIntentReadAccess();
    CommandDescriptor currentCommand = this.currentCommand;
    LOG.assertTrue(currentCommand != null);
    this.currentCommand = currentCommand.withName(name);
  }

  @Override
  public void setCurrentCommandGroupId(Object groupId) {
    ThreadingAssertions.assertWriteIntentReadAccess();
    CommandDescriptor currentCommand = this.currentCommand;
    LOG.assertTrue(currentCommand != null);
    this.currentCommand = currentCommand.withGroupId(groupId);
  }

  @Override
  public @Nullable Runnable getCurrentCommand() {
    CommandDescriptor currentCommand = this.currentCommand;
    return currentCommand != null ? currentCommand.getCommand() : null;
  }

  @Override
  public @Nullable String getCurrentCommandName() {
    CommandDescriptor currentCommand = this.currentCommand;
    if (currentCommand != null) {
      return currentCommand.getName();
    }
    if (!interruptedCommands.isEmpty()) {
      CommandDescriptor command = interruptedCommands.peek();
      return command != null ? command.getName() : null;
    }
    return null;
  }

  @Override
  public @Nullable Object getCurrentCommandGroupId() {
    CommandDescriptor currentCommand = this.currentCommand;
    if (currentCommand != null) {
      return currentCommand.getGroupId();
    }
    if (!interruptedCommands.isEmpty()) {
      final CommandDescriptor command = interruptedCommands.peek();
      return command != null ? command.getGroupId() : null;
    }
    return null;
  }

  @Override
  public @Nullable Project getCurrentCommandProject() {
    CommandDescriptor currentCommand = this.currentCommand;
    return currentCommand != null ? currentCommand.getProject() : null;
  }

  @Override
  public void addCommandListener(@NotNull CommandListener listener) {
    eventPublisher.addCommandListener(listener);
  }

  @Override
  public void runUndoTransparentAction(@NotNull Runnable action) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("runUndoTransparentAction: " + action + ", in command = " + (currentCommand != null) +
                ", in transparent action = " + isUndoTransparentActionInProgress());
    }
    if (undoTransparentCount++ == 0) {
      eventPublisher.undoTransparentActionStarted();
    }
    try {
      action.run();
    }
    finally {
      if (undoTransparentCount == 1) {
        eventPublisher.beforeUndoTransparentActionFinished();
      }
      if (--undoTransparentCount == 0) {
        eventPublisher.undoTransparentActionFinished();
      }
    }
  }

  @Override
  public final AutoCloseable withUndoTransparentAction() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("withUndoTransparentAction in command = " + (currentCommand != null) +
                ", in transparent action = " + isUndoTransparentActionInProgress());
    }
    if (undoTransparentCount++ == 0) {
      eventPublisher.undoTransparentActionStarted();
    }
    return () -> {
      if (undoTransparentCount == 1) {
        eventPublisher.beforeUndoTransparentActionFinished();
      }
      if (--undoTransparentCount == 0) {
        eventPublisher.undoTransparentActionFinished();
      }
    };
  }

  @Override
  public boolean isUndoTransparentActionInProgress() {
    return undoTransparentCount > 0;
  }

  @Override
  public void markCurrentCommandAsGlobal(@Nullable Project project) {
  }

  @Override
  public void addAffectedDocuments(@Nullable Project project, Document @NotNull ... docs) {
  }

  @Override
  public void addAffectedFiles(@Nullable Project project, VirtualFile @NotNull ... files) {
  }

  @ApiStatus.Internal
  @ApiStatus.Experimental
  public boolean isMergeGlobalCommandsAllowed() {
    return allowMergeGlobalCommandsCount > 0;
  }

  @Override
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public AccessToken allowMergeGlobalCommands() {
    ThreadingAssertions.assertWriteIntentReadAccess();
    allowMergeGlobalCommandsCount++;

    return new AccessToken() {
      @Override
      public void finish() {
        ThreadingAssertions.assertWriteIntentReadAccess();
        allowMergeGlobalCommandsCount--;
      }
    };
  }

  @Override
  public void allowMergeGlobalCommands(@NotNull Runnable action) {
    try (AccessToken ignored = allowMergeGlobalCommands()) {
      action.run();
    }
  }

  @ApiStatus.Internal
  protected boolean isCommandTokenActive(@NotNull CommandToken command) {
    return command.equals(currentCommand);
  }

  private void fireCommandStarted(@NotNull CommandDescriptor command) {
    CommandEvent event = command.toCommandEvent(this);
    eventPublisher.commandStarted(event);
  }

  private void fireCommandFinished(@NotNull CommandDescriptor command) {
    CommandEvent event = command.toCommandEvent(this);
    try {
      eventPublisher.beforeCommandFinished(event);
    }
    finally {
      this.currentCommand = null;
      eventPublisher.commandFinished(event);
    }
    LOG.debug("finishCommand: name = " + event.getCommandName() + ", groupId = " + event.getCommandGroupId());
  }

  private static @Nullable Document getDocumentFromGroupId(@Nullable Object groupId) {
    if (groupId instanceof Document) {
      return (Document) groupId;
    }
    if (groupId instanceof Ref) {
      Object value = ((Ref<?>) groupId).get();
      if (value instanceof Document) {
        return (Document) value;
      }
    }
    return null;
  }
}
