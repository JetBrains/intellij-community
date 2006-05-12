/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.command;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Nls;

public abstract class CommandProcessor {
  public static CommandProcessor getInstance(){
    return ApplicationManager.getApplication().getComponent(CommandProcessor.class);
  }

  /**
   * @deprecated use {@link #executeCommand(com.intellij.openapi.project.Project, java.lang.Runnable, java.lang.String, java.lang.Object)}
   */
  public abstract void executeCommand(Runnable runnable, @Nls String name, Object groupId);
  public abstract void executeCommand(Project project, Runnable runnable, @Nls String name, Object groupId);
  public abstract void executeCommand(Project project, Runnable runnable, @Nls String name, Object groupId, UndoConfirmationPolicy undoConfirmationPolicy);

  public abstract void setCurrentCommandName(@Nls String name);
  public abstract void setCurrentCommandGroupId(Object groupId);

  @Nullable
  public abstract Runnable getCurrentCommand();
  @Nullable
  public abstract String getCurrentCommandName();
  @Nullable
  public abstract Object getCurrentCommandGroupId();
  @Nullable
  public abstract Project getCurrentCommandProject();

  public abstract void addCommandListener(CommandListener listener);
  public abstract void addCommandListener(CommandListener listener, Disposable parentDisposable);
  public abstract void removeCommandListener(CommandListener listener);

  public abstract void runUndoTransparentAction(Runnable action);
  public abstract boolean isUndoTransparentActionInProgress();

  public abstract void markCurrentCommandAsComplex(Project project);
}
