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
package com.intellij.ide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author mike
 */
@SuppressWarnings({"MethodMayBeStatic"})
public class OpenFileXmlRpcHandler {
  public boolean open(final String absolutePath) {
    return openAndNavigate(absolutePath, -1, -1);
  }

  public boolean openAndNavigate(final String absolutePath, final int line, final int column) {
    final Application application = ApplicationManager.getApplication();

    application.invokeLater(new Runnable() {
      public void run() {
        final String path = FileUtil.toSystemIndependentName(absolutePath);
        final VirtualFile virtualFile = application.runWriteAction(new Computable<VirtualFile>() {
          @Override
          public VirtualFile compute() {
            return LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
          }
        });
        if (virtualFile == null) return;

        Project project = ProjectLocator.getInstance().guessProjectForFile(virtualFile);
        if (project == null) return;

        FileEditorProviderManager editorProviderManager = FileEditorProviderManager.getInstance();
        if (editorProviderManager.getProviders(project, virtualFile).length == 0) return;
        OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, line, column);
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
      }
    });

    return true;
  }
}
