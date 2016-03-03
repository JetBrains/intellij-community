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
package com.intellij.compiler.server;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.impl.MessagesContainer;
import com.intellij.compiler.impl.ProjectCompileScope;
import com.intellij.openapi.compiler.*;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 *         Date: 27-Jan-16
 */
final class AutomakeCompileContext extends UserDataHolderBase implements CompileContext {
  private final Project myProject;
  private final ProjectCompileScope myScope;
  private final MessagesContainer myMessages;
  private final EmptyProgressIndicator myIndicator;
  private final boolean myAnnotationProcessingEnabled;

  public AutomakeCompileContext(Project project) {
    myProject = project;
    myScope = new ProjectCompileScope(project);
    myMessages = new MessagesContainer(project);
    myIndicator = new EmptyProgressIndicator();
    myAnnotationProcessingEnabled = CompilerConfiguration.getInstance(project).isAnnotationProcessorsEnabled();
  }

  @Override
  public Project getProject() {
    return myProject;
  }

  @Override
  public CompileScope getCompileScope() {
    return getProjectCompileScope();
  }

  @Override
  public CompileScope getProjectCompileScope() {
    return myScope;
  }

  @Override
  public boolean isMake() {
    return true;
  }

  @Override
  public boolean isAutomake() {
    return true;
  }

  public void addMessage(CompilerMessageCategory category, String message, @Nullable String url, int lineNum, int columnNum) {
    addMessage(category, message, url, lineNum, columnNum, null);
  }

  @Override
  public void addMessage(CompilerMessageCategory category, String message, @Nullable String url, int lineNum, int columnNum, Navigatable navigatable) {
    createAndAddMessage(category, message, url, lineNum, columnNum, navigatable);
  }

  @Override
  public CompilerMessage[] getMessages(CompilerMessageCategory category) {
    return myMessages.getMessages(category).toArray(CompilerMessage.EMPTY_ARRAY);
  }

  @Nullable
  public CompilerMessage createAndAddMessage(CompilerMessageCategory category, String message, @Nullable String url, int lineNum, int columnNum, Navigatable navigatable) {
    return myMessages.addMessage(category, message, url, lineNum, columnNum, navigatable);
  }

  @Override
  public int getMessageCount(CompilerMessageCategory category) {
    return myMessages.getMessageCount(category);
  }

  @NotNull
  @Override
  public ProgressIndicator getProgressIndicator() {
    return myIndicator;
  }

  @Override
  public void requestRebuildNextTime(String message) {
  }

  @Override
  public boolean isRebuildRequested() {
    return false;
  }

  @Nullable
  @Override
  public String getRebuildReason() {
    return null;
  }

  @Override
  public Module getModuleByFile(VirtualFile file) {
    return ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);
  }

  public VirtualFile getModuleOutputDirectory(final Module module) {
    return CompilerPaths.getModuleOutputDirectory(module, false);
  }

  public VirtualFile getModuleOutputDirectoryForTests(final Module module) {
    return CompilerPaths.getModuleOutputDirectory(module, true);
  }

  @Override
  public boolean isRebuild() {
    return false;
  }

  @Override
  public boolean isAnnotationProcessorsEnabled() {
    return myAnnotationProcessingEnabled;
  }
}
