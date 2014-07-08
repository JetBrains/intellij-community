/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.scratch;

import com.intellij.lang.Language;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import org.jetbrains.annotations.NotNull;

public class ScratchpadManagerImpl extends ScratchpadManager implements Disposable {
  private final Project myProject;
  private Integer myIndex = 0;
  private Language myLatestLanguage;

  public ScratchpadManagerImpl(@NotNull Project project) {
    myProject = project;
    StatusBar statusBar = WindowManager.getInstance().getStatusBar(myProject);
    if (statusBar == null) return;
    if (statusBar.getWidget(ScratchWidget.ID) != null) return;
    ScratchWidget widget = new ScratchWidget(myProject);
    statusBar.addWidget(widget, "before Encoding", myProject);
    statusBar.updateWidget(ScratchWidget.ID);
    project.getMessageBus().connect(project).subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, widget);
  }

  @NotNull
  @Override
  public VirtualFile createScratchFile(@NotNull final Language language) {
    myLatestLanguage = language;
    return ApplicationManager.getApplication().runWriteAction(new Computable<VirtualFile>() {
      @Override
      public VirtualFile compute() {
        String name = generateFileName();
        return ScratchpadFileSystem.getScratchFileSystem().addFile(name, language, calculatePrefix(ScratchpadManagerImpl.this.myProject));
      }
    });
  }

  @NotNull
  private static String calculatePrefix(@NotNull Project project) {
    return project.getLocationHash();
  }

  @Override
  public Language getLatestLanguage() {
    return myLatestLanguage;
  }

  @NotNull
  private String generateFileName() {
    int updated = myIndex++;
    String index = updated == 0 ? "" : "." + updated;
    return "scratch" + index;
  }

  @Override
  public void dispose() {
    ScratchpadFileSystem.getScratchFileSystem().removeByPrefix(calculatePrefix(myProject));
  }
}