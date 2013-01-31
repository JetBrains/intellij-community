/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.dvcs.test;

import com.intellij.dvcs.DvcsPlatformFacade;
import com.intellij.ide.SaveAndSyncHandler;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.mock.MockLocalFileSystem;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ex.ProjectManagerEx;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.vcs.MockChangeListManager;
import org.easymock.classextension.EasyMock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;

/**
 * 
 * @author Kirill Likhodedov
 */
public abstract class DvcsTestPlatformFacade implements DvcsPlatformFacade {

  private MockProjectRootManager myProjectRootManager;
  private ChangeListManagerEx myChangeListManager;
  private MockVcsHelper myVcsHelper;

  public DvcsTestPlatformFacade() {
    myProjectRootManager = new MockProjectRootManager();
    myChangeListManager = new MockChangeListManager();
  }

  @NotNull
  @Override
  public ProjectRootManager getProjectRootManager(@NotNull Project project) {
    return myProjectRootManager;
  }

  @Override
  public <T> T runReadAction(@NotNull Computable<T> computable) {
    return computable.compute();
  }

  @Override
  public void runReadAction(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void runWriteAction(@NotNull Runnable runnable) {
    runnable.run();
  }

  @Override
  public void invokeAndWait(@NotNull Runnable runnable, @NotNull ModalityState modalityState) {
    runnable.run();
  }

  @Override
  public void executeOnPooledThread(@NotNull Runnable runnable) {
    new Thread(runnable).start();
  }

  @Override
  public ChangeListManagerEx getChangeListManager(@NotNull Project project) {
    return myChangeListManager;
  }

  @Override
  public LocalFileSystem getLocalFileSystem() {
    return new MockLocalFileSystem();
  }

  @NotNull
  @Override
  public AbstractVcsHelper getVcsHelper(@NotNull Project project) {
    if (myVcsHelper == null) {
      myVcsHelper = new MockVcsHelper(project);
    }
    return myVcsHelper;
  }

  @Nullable
  @Override
  public IdeaPluginDescriptor getPluginByClassName(@NotNull String name) {
    return null;
  }

  @NotNull
  @Override
  public String getLineSeparator(@NotNull VirtualFile file, boolean detect) {
    try {
      return FileUtil.loadFile(new File(file.getPath())).contains("\r\n") ? "\r\n" : "\n";
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void saveAllDocuments() {
  }

  @Nullable
  @Override
  public VirtualFile getVirtualFileByPath(@NotNull String path) {
    return new MockVirtualFile(path);
  }

  @NotNull
  @Override
  public ProjectManagerEx getProjectManager() {
    return EasyMock.createMock(ProjectManagerEx.class);
  }

  @NotNull
  @Override
  public SaveAndSyncHandler getSaveAndSyncHandler() {
    return EasyMock.createMock(SaveAndSyncHandler.class);
  }

  @Override
  public void hardRefresh(@NotNull VirtualFile root) {
  }
}
