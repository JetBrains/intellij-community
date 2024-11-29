// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.*;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.ThreadingAssertions;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CoreCommandProcessor extends CommandProcessorEx {
  private static class CommandDescriptor implements CommandToken {
    public final @NotNull Runnable myCommand;
    public final Project myProject;
    public @NlsContexts.Command String myName;
    public Object myGroupId;
    public final Document myDocument;
    final @NotNull UndoConfirmationPolicy myUndoConfirmationPolicy;
    final boolean myShouldRecordActionForActiveDocument;

    CommandDescriptor(@NotNull Runnable command,
                      Project project,
                      @NlsContexts.Command String name,
                      Object groupId,
                      @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
                      boolean shouldRecordActionForActiveDocument,
                      Document document) {
      myCommand = command;
      myProject = project;
      myName = name;
      myGroupId = groupId;
      myUndoConfirmationPolicy = undoConfirmationPolicy;
      myShouldRecordActionForActiveDocument = shouldRecordActionForActiveDocument;
      myDocument = document;
    }

    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public String toString() {
      return "'" + myName + "', group: '" + myGroupId + "'";
    }
  }

  protected CommandDescriptor myCurrentCommand;
  // Stack is used instead of ConcurrentLinkedDeque because null values are not supported by ConcurrentLinkedDeque
  private final Stack<CommandDescriptor> myInterruptedCommands = new Stack<>();
  private final List<CommandListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();
  private int myUndoTransparentCount;
  private int myAllowMergeGlobalCommandsCount = 0;

  private final CommandListener eventPublisher;

  public CoreCommandProcessor() {
    MessageBus messageBus = ApplicationManager.getApplication().getMessageBus();
    messageBus.simpleConnect().subscribe(CommandListener.TOPIC, new CommandListener() {
      @Override
      public void commandStarted(@NotNull CommandEvent event) {
        for (CommandListener listener : myListeners) {
          try {
            listener.commandStarted(event);
          }
          catch (Throwable e) {
            CommandLog.LOG.error(e);
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
            CommandLog.LOG.error(e);
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
            CommandLog.LOG.error(e);
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
            CommandLog.LOG.error(e);
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
            CommandLog.LOG.error(e);
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
            CommandLog.LOG.error(e);
          }
        }
      }
    });

    // will, command events occurred quite often, let's cache publisher
    eventPublisher = messageBus.syncPublisher(CommandListener.TOPIC);
  }

  @Override
  public void executeCommand(Project project, @NotNull Runnable runnable, String name, Object groupId) {
    executeCommand(project, runnable, name, groupId, UndoConfirmationPolicy.DEFAULT);
  }

  @Override
  public void executeCommand(Project project, @NotNull Runnable runnable, String name, Object groupId, Document document) {
    executeCommand(project, runnable, name, groupId, UndoConfirmationPolicy.DEFAULT, document);
  }

  @Override
  public void executeCommand(Project project,
                             final @NotNull Runnable command,
                             final String name,
                             final Object groupId,
                             @NotNull UndoConfirmationPolicy undoConfirmationPolicy) {
    executeCommand(project, command, name, groupId, undoConfirmationPolicy, null);
  }

  @Override
  public void executeCommand(Project project,
                             final @NotNull Runnable command,
                             final String name,
                             final Object groupId,
                             @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
                             Document document) {
    executeCommand(project, command, name, groupId, undoConfirmationPolicy, true, document);
  }

  @Override
  public void executeCommand(@Nullable Project project,
                             @NotNull Runnable command,
                             @Nullable String name,
                             @Nullable Object groupId,
                             @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
                             boolean shouldRecordCommandForActiveDocument) {
    executeCommand(project, command, name, groupId, undoConfirmationPolicy, shouldRecordCommandForActiveDocument, null);
  }

  @Override
  public void executeCommand(@Nullable Project project,
                              @NotNull Runnable command,
                              @Nullable @NlsContexts.Command String name,
                              @Nullable Object groupId,
                              @NotNull UndoConfirmationPolicy undoConfirmationPolicy,
                              boolean shouldRecordCommandForActiveDocument,
                              @Nullable Document document) {
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();

    if (CommandLog.LOG.isDebugEnabled()) {
      String currentCommandName;
      if (myCurrentCommand != null) currentCommandName = myCurrentCommand.myName;
      else currentCommandName = "<null>";
      CommandLog.LOG.debug("executeCommand: " + command + ", name = " + name + ", groupId = " + groupId +
                           ", in command = " + currentCommandName +
                           ", in transparent action = " + isUndoTransparentActionInProgress());
    }

    if (project != null && project.isDisposed()) {
      CommandLog.LOG.error("Project "+project+" already disposed");
      return;
    }

    if (myCurrentCommand != null) {
      application.runWriteIntentReadAction(() -> { command.run(); return null; });
      return;
    }
    CommandDescriptor descriptor = new CommandDescriptor(command, project, name, groupId, undoConfirmationPolicy,
                                                         shouldRecordCommandForActiveDocument, document);
    myCurrentCommand = descriptor;
    application.runWriteIntentReadAction(() -> {
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
      return null;
    });
  }

  @Override
  public @Nullable CommandToken startCommand(final @Nullable Project project,
                                             final String name,
                                             final @Nullable Object groupId,
                                             final @NotNull UndoConfirmationPolicy undoConfirmationPolicy) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    if (project != null && project.isDisposed()) return null;

    if (CommandLog.LOG.isDebugEnabled()) {
      CommandLog.LOG.debug("startCommand: name = " + name + ", groupId = " + groupId);
    }

    if (myCurrentCommand != null) {
      return null;
    }

    Document document = groupId instanceof Document
                        ? (Document)groupId
                        : groupId instanceof Ref && ((Ref<?>)groupId).get() instanceof Document
                           ? (Document)((Ref<?>)groupId).get()
                           : null;
    myCurrentCommand = new CommandDescriptor(EmptyRunnable.INSTANCE, project, name, groupId, undoConfirmationPolicy, true, document);
    fireCommandStarted();
    return myCurrentCommand;
  }

  @Override
  public void finishCommand(@NotNull CommandToken command, @Nullable Throwable throwable) {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    CommandLog.LOG.assertTrue(myCurrentCommand != null, "no current command in progress");
    fireCommandFinished();
  }

  private void fireCommandFinished() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    CommandDescriptor currentCommand = myCurrentCommand;
    CommandEvent event = new CommandEvent(this, currentCommand.myCommand,
                                          currentCommand.myName,
                                          currentCommand.myGroupId,
                                          currentCommand.myProject,
                                          currentCommand.myUndoConfirmationPolicy,
                                          currentCommand.myShouldRecordActionForActiveDocument,
                                          currentCommand.myDocument);
    CommandListener publisher = eventPublisher;
    try {
      publisher.beforeCommandFinished(event);
    }
    finally {
      myCurrentCommand = null;
      publisher.commandFinished(event);
    }

    CommandLog.LOG.debug("finishCommand: name = " + event.getCommandName() + ", groupId = " + event.getCommandGroupId());
  }

  @Override
  public void enterModal() {
    ThreadingAssertions.assertWriteIntentReadAccess();
    CommandDescriptor currentCommand = myCurrentCommand;
    myInterruptedCommands.push(currentCommand);
    if (currentCommand != null) {
      fireCommandFinished();
    }
  }

  @Override
  public void leaveModal() {
    ThreadingAssertions.assertWriteIntentReadAccess();
    CommandLog.LOG.assertTrue(myCurrentCommand == null, "Command must not run: " + myCurrentCommand);

    myCurrentCommand = myInterruptedCommands.pop();
    if (myCurrentCommand != null) {
      fireCommandStarted();
    }
  }

  @Override
  public void setCurrentCommandName(String name) {
    ThreadingAssertions.assertWriteIntentReadAccess();
    CommandDescriptor currentCommand = myCurrentCommand;
    CommandLog.LOG.assertTrue(currentCommand != null);
    currentCommand.myName = name;
  }

  @Override
  public void setCurrentCommandGroupId(Object groupId) {
    ThreadingAssertions.assertWriteIntentReadAccess();
    CommandDescriptor currentCommand = myCurrentCommand;
    CommandLog.LOG.assertTrue(currentCommand != null);
    currentCommand.myGroupId = groupId;
  }

  @Override
  public @Nullable Runnable getCurrentCommand() {
    CommandDescriptor currentCommand = myCurrentCommand;
    return currentCommand != null ? currentCommand.myCommand : null;
  }

  @Override
  public @Nullable String getCurrentCommandName() {
    CommandDescriptor currentCommand = myCurrentCommand;
    if (currentCommand != null) return currentCommand.myName;
    if (!myInterruptedCommands.isEmpty()) {
      final CommandDescriptor command = myInterruptedCommands.peek();
      return command != null ? command.myName : null;
    }
    return null;
  }

  @Override
  public @Nullable Object getCurrentCommandGroupId() {
    CommandDescriptor currentCommand = myCurrentCommand;
    if (currentCommand != null) return currentCommand.myGroupId;
    if (!myInterruptedCommands.isEmpty()) {
      final CommandDescriptor command = myInterruptedCommands.peek();
      return command != null ? command.myGroupId : null;
    }
    return null;
  }

  @Override
  public @Nullable Project getCurrentCommandProject() {
    CommandDescriptor currentCommand = myCurrentCommand;
    return currentCommand != null ? currentCommand.myProject : null;
  }

  @Override
  public void addCommandListener(@NotNull CommandListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void runUndoTransparentAction(@NotNull Runnable action) {
    if (CommandLog.LOG.isDebugEnabled()) {
      CommandLog.LOG.debug("runUndoTransparentAction: " + action + ", in command = " + (myCurrentCommand != null) +
                           ", in transparent action = " + isUndoTransparentActionInProgress());
    }
    if (myUndoTransparentCount++ == 0) {
      eventPublisher.undoTransparentActionStarted();
    }
    try {
      action.run();
    }
    finally {
      if (myUndoTransparentCount == 1) {
        eventPublisher.beforeUndoTransparentActionFinished();
      }
      if (--myUndoTransparentCount == 0) {
        eventPublisher.undoTransparentActionFinished();
      }
    }
  }

  @Override
  public final AutoCloseable withUndoTransparentAction() {
    if (CommandLog.LOG.isDebugEnabled()) {
      CommandLog.LOG.debug("withUndoTransparentAction in command = " + (myCurrentCommand != null) +
                           ", in transparent action = " + isUndoTransparentActionInProgress());
    }
    if (myUndoTransparentCount++ == 0) {
      eventPublisher.undoTransparentActionStarted();
    }
    return () -> {
      if (myUndoTransparentCount == 1) {
        eventPublisher.beforeUndoTransparentActionFinished();
      }
      if (--myUndoTransparentCount == 0) {
        eventPublisher.undoTransparentActionFinished();
      }
    };
  }

  @Override
  public boolean isUndoTransparentActionInProgress() {
    return myUndoTransparentCount > 0;
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
  public Boolean isMergeGlobalCommandsAllowed() {
    return myAllowMergeGlobalCommandsCount > 0;
  }

  @Override
  @ApiStatus.Internal
  @ApiStatus.Experimental
  public AccessToken allowMergeGlobalCommands() {
    ThreadingAssertions.assertWriteIntentReadAccess();
    myAllowMergeGlobalCommandsCount++;

    return new AccessToken() {
      @Override
      public void finish() {
        ThreadingAssertions.assertWriteIntentReadAccess();
        myAllowMergeGlobalCommandsCount--;
      }
    };
  }

  @Override
  public void allowMergeGlobalCommands(@NotNull Runnable action) {
    try (AccessToken ignored = allowMergeGlobalCommands()) {
      action.run();
    }
  }

  private void fireCommandStarted() {
    ApplicationManager.getApplication().assertWriteIntentLockAcquired();
    CommandDescriptor currentCommand = myCurrentCommand;
    CommandEvent event = new CommandEvent(this,
                                          currentCommand.myCommand,
                                          currentCommand.myName,
                                          currentCommand.myGroupId,
                                          currentCommand.myProject,
                                          currentCommand.myUndoConfirmationPolicy,
                                          currentCommand.myShouldRecordActionForActiveDocument,
                                          currentCommand.myDocument);
    eventPublisher.commandStarted(event);
  }
}
