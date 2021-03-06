// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * @author Eugene Zhuravlev
 */
final class AutomakeCompileContext extends UserDataHolderBase implements CompileContext {
  private final Project myProject;
  private final ProjectCompileScope myScope;
  private final MessagesContainer myMessages;
  private final ProgressIndicator myIndicator;
  private final boolean myAnnotationProcessingEnabled;

  AutomakeCompileContext(@NotNull Project project) {
    myProject = project;
    myScope = new ProjectCompileScope(project);
    myMessages = new MessagesContainer(project);
    myIndicator = new EmptyProgressIndicator();
    myAnnotationProcessingEnabled = CompilerConfiguration.getInstance(project).isAnnotationProcessorsEnabled();
  }

  @NotNull
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


  @Override
  public void addMessage(@NotNull CompilerMessageCategory category,
                         @Nls(capitalization = Nls.Capitalization.Sentence) String message,
                         @Nullable String url, int lineNum, int columnNum, @Nullable Navigatable navigatable,
                         Collection<String> moduleNames) {
    createAndAddMessage(category, message, url, lineNum, columnNum, navigatable, moduleNames);
  }

  @Override
  public CompilerMessage @NotNull [] getMessages(@NotNull CompilerMessageCategory category) {
    return myMessages.getMessages(category).toArray(CompilerMessage.EMPTY_ARRAY);
  }

  @Nullable
  CompilerMessage createAndAddMessage(CompilerMessageCategory category,
                                      @Nls String message,
                                      @Nullable String url, int lineNum, int columnNum, Navigatable navigatable,
                                      final Collection<String> moduleNames) {
    return myMessages.addMessage(category, message, url, lineNum, columnNum, navigatable, moduleNames);
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
  public Module getModuleByFile(@NotNull VirtualFile file) {
    return ProjectRootManager.getInstance(myProject).getFileIndex().getModuleForFile(file);
  }

  @Override
  public VirtualFile getModuleOutputDirectory(@NotNull final Module module) {
    return CompilerPaths.getModuleOutputDirectory(module, false);
  }

  @Override
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
