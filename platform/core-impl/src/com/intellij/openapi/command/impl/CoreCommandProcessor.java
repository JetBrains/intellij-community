/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.command.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.CommandProcessorEx;
import com.intellij.openapi.command.UndoConfirmationPolicy;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Stack;

public class CoreCommandProcessor extends CommandProcessorEx {
  private static class CommandDescriptor {
    public final Runnable myCommand;
    public final Project myProject;
    public String myName;
    public Object myGroupId;
    public final Document myDocument;
    public final UndoConfirmationPolicy myUndoConfirmationPolicy;

    public CommandDescriptor(Runnable command,
                             Project project,
                             String name,
                             Object groupId,
                             UndoConfirmationPolicy undoConfirmationPolicy,
                             Document document) {
      myCommand = command;
      myProject = project;
      myName = name;
      myGroupId = groupId;
      myUndoConfirmationPolicy = undoConfirmationPolicy;
      myDocument = document;
    }

    @Override
    public String toString() {
      return "'" + myName + "', group: '" + myGroupId + "'";
    }
  }

  protected CommandDescriptor myCurrentCommand = null;
  private final Stack<CommandDescriptor> myInterruptedCommands = new Stack<CommandDescriptor>();

//  private HashMap myStatisticsMap = new HashMap(); // command name --> count

  //  private HashMap myStatisticsMap = new HashMap(); // command name --> count

  private final List<CommandListener> myListeners = ContainerUtil.createLockFreeCopyOnWriteList();

  private int myUndoTransparentCount = 0;

  @Override
  public void executeCommand(@NotNull Runnable runnable, String name, Object groupId) {
    executeCommand(null, runnable, name, groupId);
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
                             @NotNull final Runnable command,
                             final String name,
                             final Object groupId,
                             @NotNull UndoConfirmationPolicy confirmationPolicy) {
    executeCommand(project, command, name, groupId, confirmationPolicy, null);
  }

  @Override
  public void executeCommand(Project project,
                             @NotNull final Runnable command,
                             final String name,
                             final Object groupId,
                             @NotNull UndoConfirmationPolicy confirmationPolicy,
                             Document document) {
    Application application = ApplicationManager.getApplication();
    application.assertIsDispatchThread();
    if (project != null && project.isDisposed()) {
      if (application.isUnitTestMode()) {
        CommandLog.LOG.error("Project "+project+" already disposed");
      }
      return;
    }

    if (CommandLog.LOG.isDebugEnabled()) {
      CommandLog.LOG.debug("executeCommand: " + command + ", name = " + name + ", groupId = " + groupId);
    }

    if (myCurrentCommand != null) {
      command.run();
      return;
    }
    Throwable throwable = null;
    try {
      myCurrentCommand = new CommandDescriptor(command, project, name, groupId, confirmationPolicy, document);
      fireCommandStarted();
      command.run();
    }
    catch (Throwable th) {
      throwable = th;
    }
    finally {
      finishCommand(project, myCurrentCommand, throwable);
    }
  }

  @Override
  @Nullable
  public Object startCommand(final Project project,
                             @Nls final String name,
                             final Object groupId,
                             final UndoConfirmationPolicy undoConfirmationPolicy) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    if (project != null && project.isDisposed()) return null;

    if (CommandLog.LOG.isDebugEnabled()) {
      CommandLog.LOG.debug("startCommand: name = " + name + ", groupId = " + groupId);
    }

    if (myCurrentCommand != null) {
      return null;
    }

    Document document = groupId instanceof Ref && ((Ref)groupId).get() instanceof Document ? (Document)((Ref)groupId).get() : null;
    myCurrentCommand = new CommandDescriptor(EmptyRunnable.INSTANCE, project, name, groupId, undoConfirmationPolicy, document);
    fireCommandStarted();
    return myCurrentCommand;
  }

  @Override
  public void finishCommand(final Project project, final Object command, final Throwable throwable) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CommandLog.LOG.assertTrue(myCurrentCommand != null, "no current command in progress");
    fireCommandFinished();
  }

  protected void fireCommandFinished() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CommandDescriptor currentCommand = myCurrentCommand;
    CommandEvent event = new CommandEvent(this, currentCommand.myCommand,
                                          currentCommand.myName,
                                          currentCommand.myGroupId,
                                          currentCommand.myProject,
                                          currentCommand.myUndoConfirmationPolicy,
                                          currentCommand.myDocument);
    try {
      for (CommandListener listener : myListeners) {
        try {
          listener.beforeCommandFinished(event);
        }
        catch (Throwable e) {
          CommandLog.LOG.error(e);
        }
      }
    }
    finally {
      myCurrentCommand = null;
      for (CommandListener listener : myListeners) {
        try {
          listener.commandFinished(event);
        }
        catch (Throwable e) {
          CommandLog.LOG.error(e);
        }
      }
    }
  }

  @Override
  public void enterModal() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CommandDescriptor currentCommand = myCurrentCommand;
    myInterruptedCommands.push(currentCommand);
    if (currentCommand != null) {
      fireCommandFinished();
    }
  }

  @Override
  public void leaveModal() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CommandLog.LOG.assertTrue(myCurrentCommand == null, "Command must not run: " + String.valueOf(myCurrentCommand));

    myCurrentCommand = myInterruptedCommands.pop();
    if (myCurrentCommand != null) {
      fireCommandStarted();
    }
  }

  @Override
  public void setCurrentCommandName(String name) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CommandDescriptor currentCommand = myCurrentCommand;
    CommandLog.LOG.assertTrue(currentCommand != null);
    currentCommand.myName = name;
  }

  @Override
  public void setCurrentCommandGroupId(Object groupId) {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CommandDescriptor currentCommand = myCurrentCommand;
    CommandLog.LOG.assertTrue(currentCommand != null);
    currentCommand.myGroupId = groupId;
  }

  @Override
  @Nullable
  public Runnable getCurrentCommand() {
    CommandDescriptor currentCommand = myCurrentCommand;
    return currentCommand != null ? currentCommand.myCommand : null;
  }

  @Override
  @Nullable
  public String getCurrentCommandName() {
    CommandDescriptor currentCommand = myCurrentCommand;
    if (currentCommand != null) return currentCommand.myName;
    if (!myInterruptedCommands.isEmpty()) {
      final CommandDescriptor command = myInterruptedCommands.peek();
      return command != null ? command.myName : null;
    }
    return null;
  }

  @Override
  @Nullable
  public Object getCurrentCommandGroupId() {
    CommandDescriptor currentCommand = myCurrentCommand;
    if (currentCommand != null) return currentCommand.myGroupId;
    if (!myInterruptedCommands.isEmpty()) {
      final CommandDescriptor command = myInterruptedCommands.peek();
      return command != null ? command.myGroupId : null;
    }
    return null;
  }

  @Override
  @Nullable
  public Project getCurrentCommandProject() {
    CommandDescriptor currentCommand = myCurrentCommand;
    return currentCommand != null ? currentCommand.myProject : null;
  }

  @Override
  public void addCommandListener(@NotNull CommandListener listener) {
    myListeners.add(listener);
  }

  @Override
  public void addCommandListener(@NotNull final CommandListener listener, @NotNull Disposable parentDisposable) {
    addCommandListener(listener);
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        removeCommandListener(listener);
      }
    });
  }

  @Override
  public void removeCommandListener(@NotNull CommandListener listener) {
    myListeners.remove(listener);
  }

  @Override
  public void runUndoTransparentAction(@NotNull Runnable action) {
    if (myUndoTransparentCount++ == 0) fireUndoTransparentStarted();
    try {
      action.run();
    }
    finally {
      if (--myUndoTransparentCount == 0) fireUndoTransparentFinished();
    }
  }

  @Override
  public boolean isUndoTransparentActionInProgress() {
    return myUndoTransparentCount > 0;
  }

  @Override
  public void markCurrentCommandAsGlobal(Project project) {
  }


  @Override
  public void addAffectedDocuments(Project project, @NotNull Document... docs) {
  }

  @Override
  public void addAffectedFiles(Project project, @NotNull VirtualFile... files) {
  }

  private void fireCommandStarted() {
    ApplicationManager.getApplication().assertIsDispatchThread();
    CommandDescriptor currentCommand = myCurrentCommand;
    CommandEvent event = new CommandEvent(this,
                                          currentCommand.myCommand,
                                          currentCommand.myName,
                                          currentCommand.myGroupId,
                                          currentCommand.myProject,
                                          currentCommand.myUndoConfirmationPolicy,
                                          currentCommand.myDocument);
    for (CommandListener listener : myListeners) {
      try {
        listener.commandStarted(event);
      }
      catch (Throwable e) {
        CommandLog.LOG.error(e);
      }
    }
  }

  private void fireUndoTransparentStarted() {
    for (CommandListener listener : myListeners) {
      try {
        listener.undoTransparentActionStarted();
      }
      catch (Throwable e) {
        CommandLog.LOG.error(e);
      }
    }
  }

  private void fireUndoTransparentFinished() {
    for (CommandListener listener : myListeners) {
      try {
        listener.undoTransparentActionFinished();
      }
      catch (Throwable e) {
        CommandLog.LOG.error(e);
      }
    }
  }
}
