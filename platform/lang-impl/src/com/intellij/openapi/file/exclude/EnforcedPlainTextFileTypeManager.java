/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileBasedIndex;

/**
 * @author Rustam Vishnyakov
 */
@State(name = "EnforcedPlainTextFileTypeManager", storages = {@Storage( file = "$APP_CONFIG$/plainTextFiles.xml")})
public class EnforcedPlainTextFileTypeManager extends PersistentFileSetManager {
  
  public boolean isMarkedAsPlainText(VirtualFile file) {
    return containsFile(file);
  }

  public static boolean isApplicableFor(VirtualFile file) {
    if (file.isDirectory()) return false;
    FileType originalType = FileTypeManager.getInstance().getFileTypeByFileName(file.getName());
    if (originalType.isBinary() ||
        originalType == FileTypes.PLAIN_TEXT ||
        originalType == StdFileTypes.JAVA) {
      return false;
    }
    return true;
  }

  public void markAsPlainText(VirtualFile... files) {
    for (VirtualFile file : files) {
      if (addFile(file)) {
        FileBasedIndex.getInstance().requestReindex(file);
      }
    }
    fireRootsChanged();
  }
  
  public void unmarkPlainText(VirtualFile... files) {
    for (VirtualFile file : files) {
      if (removeFile(file)) {
        FileBasedIndex.getInstance().requestReindex(file);
      }
    }
    fireRootsChanged();
  }

  private static void fireRootsChanged() {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
          ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true);
        }
      }
    });
  }

  private static EnforcedPlainTextFileTypeManager ourInstance;

  public static EnforcedPlainTextFileTypeManager getInstance() {
    if (ourInstance == null) {
      ourInstance = ServiceManager.getService(EnforcedPlainTextFileTypeManager.class);
    }
    return ourInstance;
  }
}
