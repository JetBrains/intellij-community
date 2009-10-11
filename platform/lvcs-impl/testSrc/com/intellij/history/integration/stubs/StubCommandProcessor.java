/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.history.integration.stubs;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.editor.Document;
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

  public void executeCommand(Project project, Runnable command, String name, Object groupId, UndoConfirmationPolicy undoConfirmationPolicy,
                             Document document) {
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

  public void markCurrentCommandAsGlobal(Project project) {
    throw new UnsupportedOperationException();
  }
}
