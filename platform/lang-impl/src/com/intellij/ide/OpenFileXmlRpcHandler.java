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
package com.intellij.ide;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsRoot;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author mike
 */
public class OpenFileXmlRpcHandler {
  private static final Logger LOG = Logger.getInstance(OpenFileXmlRpcHandler.class);

  // XML-RPC interface method - keep the signature intact
  @SuppressWarnings("UnusedDeclaration")
  public boolean open(String path) {
    LOG.debug("open(" + path + ")");
    return doOpen(path, -1, -1);
  }

  // XML-RPC interface method - keep the signature intact
  @SuppressWarnings("UnusedDeclaration")
  public boolean openAndNavigate(String path, int line, int column) {
    LOG.debug("openAndNavigate(" + path + ", " + line + ", " + column + ")");
    return doOpen(path, line, column);
  }

  private static boolean doOpen(final String path, final int line, final int column) {
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        Pair<VirtualFile, Project> data = new File(path).isAbsolute() ? findByAbsolutePath(path) : findByRelativePath(path);
        if (data == null) return;

        FileEditorProvider[] providers = FileEditorProviderManager.getInstance().getProviders(data.second, data.first);
        if (providers.length == 0) return;

        OpenFileDescriptor descriptor = new OpenFileDescriptor(data.second, data.first, line, column);
        FileEditorManager.getInstance(data.second).openTextEditor(descriptor, true);
      }
    });
    return true;
  }

  @Nullable
  private static Pair<VirtualFile, Project> findByAbsolutePath(String path) {
    File file = new File(FileUtil.toSystemDependentName(path));
    if (file.exists()) {
      VirtualFile vFile = findVirtualFile(file);
      if (vFile != null) {
        Project project = ProjectLocator.getInstance().guessProjectForFile(vFile);
        if (project != null) {
          return Pair.create(vFile, project);
        }
      }
    }

    return null;
  }

  @Nullable
  private static Pair<VirtualFile, Project> findByRelativePath(String path) {
    Project[] projects = ProjectManager.getInstance().getOpenProjects();
    String localPath = FileUtil.toSystemDependentName(path);

    for (Project project : projects) {
      File file = new File(project.getBasePath(), localPath);
      if (file.exists()) {
        VirtualFile vFile = findVirtualFile(file);
        return vFile != null ? Pair.create(vFile, project) : null;
      }
    }

    for (Project project : projects) {
      for (VcsRoot vcsRoot : ProjectLevelVcsManager.getInstance(project).getAllVcsRoots()) {
        VirtualFile root = vcsRoot.getPath();
        if (root != null) {
          File file = new File(FileUtil.toSystemDependentName(root.getPath()), localPath);
          if (file.exists()) {
            VirtualFile vFile = findVirtualFile(file);
            return vFile != null ? Pair.create(vFile, project) : null;
          }
        }
      }
    }

    return null;
  }

  @Nullable
  private static VirtualFile findVirtualFile(final File file) {
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      }
    });
  }
}
