// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ThreadingRuntimeFlagsKt;
import com.intellij.openapi.application.WriteIntentReadAction;
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
import com.intellij.util.ObjectUtils;
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
  private int allowMergeGlobalCommandsCount;

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
    ThreadingAssertions.assertEventDispatchThread();

    if (LOG.isDebugEnabled()) {
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
      LOG.error("Failed to start a command because " + project + " is already disposed");
      return;
    }

    if (currentCommand != null) {
      runCommandTask(command);
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
    Runnable commandTask = () -> {
      Throwable throwable = null;
      try {
        fireCommandStarted();
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
    };
    runCommandTask(commandTask);
  }

  @Override
  public @Nullable CommandToken startCommand(
    @Nullable Project project,
    @Nullable String name,
    @Nullable Object groupId,
    @NotNull UndoConfirmationPolicy undoConfirmationPolicy
  ) {
    ThreadingAssertions.assertEventDispatchThread();
    if (project != null && project.isDisposed()) {
      return null;
    }

    if (LOG.isDebugEnabled()) {
      LOG.debug("startCommand: name = " + name + ", groupId = " + groupId);
    }

    if (currentCommand != null) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("startCommand failed: name = " + name + ", groupId = " + groupId + ". " +
                  "Another command is already running: name = " + currentCommand.getName() + ", groupId = " + currentCommand.getGroupId());
      }
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
    fireCommandStarted();
    return descriptor;
  }

  @Override
  public void finishCommand(@NotNull CommandToken command, @Nullable Throwable throwable) {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.assertTrue(currentCommand != null, "no current command in progress");
    fireCommandFinished();
  }

  @Override
  public void enterModal() {
    ThreadingAssertions.assertEventDispatchThread();
    interruptedCommands.push(currentCommand);
    if (currentCommand != null) {
      fireCommandFinished();
    }
  }

  @Override
  public void leaveModal() {
    ThreadingAssertions.assertEventDispatchThread();
    if (currentCommand != null) {
      LOG.error("Command must not run: " + currentCommand);
    }
    currentCommand = interruptedCommands.pop();
    if (currentCommand != null) {
      fireCommandStarted();
    }
  }

  @Override
  public void setCurrentCommandName(String name) {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.assertTrue(currentCommand != null);
    currentCommand = currentCommand.withName(name);
  }

  @Override
  public void setCurrentCommandGroupId(Object groupId) {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.assertTrue(currentCommand != null);
    currentCommand = currentCommand.withGroupId(groupId);
  }

  @Override
  public @Nullable Runnable getCurrentCommand() {
    return ObjectUtils.doIfNotNull(currentCommand, command -> command.getCommand());
  }

  @Override
  public @Nullable String getCurrentCommandName() {
    if (currentCommand != null) {
      return currentCommand.getName();
    }
    if (!interruptedCommands.isEmpty()) {
      return ObjectUtils.doIfNotNull(interruptedCommands.peek(), command -> command.getName());
    }
    return null;
  }

  @Override
  public @Nullable Object getCurrentCommandGroupId() {
    if (currentCommand != null) {
      return currentCommand.getGroupId();
    }
    if (!interruptedCommands.isEmpty()) {
      return ObjectUtils.doIfNotNull(interruptedCommands.peek(), command -> command.getGroupId());
    }
    return null;
  }

  @Override
  public @Nullable Project getCurrentCommandProject() {
    return ObjectUtils.doIfNotNull(currentCommand, command -> command.getProject());
  }

  @Override
  public void addCommandListener(@NotNull CommandListener listener) {
    eventPublisher.addCommandListener(listener);
  }

  @Override
  public void runUndoTransparentAction(@NotNull Runnable action) {
    startUndoTransparentAction();
    try {
      action.run();
    }
    finally {
      finishUndoTransparentAction();
    }
  }

  @Override
  public final @NotNull AutoCloseable withUndoTransparentAction() {
    startUndoTransparentAction();
    return () -> finishUndoTransparentAction();
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

  private void startUndoTransparentAction() {
    if (LOG.isDebugEnabled()) {
      LOG.debug("runUndoTransparentAction in command = " + (currentCommand != null) +
                ", in transparent action = " + isUndoTransparentActionInProgress());
    }
    if (undoTransparentCount++ == 0) {
      eventPublisher.undoTransparentActionStarted();
    }
  }

  private void finishUndoTransparentAction() {
    if (undoTransparentCount == 1) {
      eventPublisher.beforeUndoTransparentActionFinished();
    }
    if (--undoTransparentCount == 0) {
      eventPublisher.undoTransparentActionFinished();
    }
  }

  private void fireCommandStarted() {
    CommandEvent event = createCurrentCommandEvent();
    eventPublisher.commandStarted(event);
  }

  private void fireCommandFinished() {
    CommandEvent event = createCurrentCommandEvent();
    try {
      eventPublisher.beforeCommandFinished(event);
    }
    finally {
      currentCommand = null;
      eventPublisher.commandFinished(event);
    }
    if (LOG.isDebugEnabled()) {
      LOG.debug("finishCommand: name = " + event.getCommandName() + ", groupId = " + event.getCommandGroupId());
    }
  }

  private @NotNull CommandEvent createCurrentCommandEvent() {
    CommandDescriptor command = currentCommand;
    if (command == null) {
      throw new IllegalStateException("No current command in progress");
    }
    return command.toCommandEvent(this);
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

  private static void runCommandTask(Runnable commandTask) {
    if (ThreadingRuntimeFlagsKt.getWrapCommandsInWriteIntent()) {
      WriteIntentReadAction.run(commandTask);
    }
    else {
      commandTask.run();
    }
  }
}
