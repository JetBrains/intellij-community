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
package com.intellij.openapi.file.exclude;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Rustam Vishnyakov
 */
@State(name = "ProjectFileExclusionManager", storages = {@Storage( file = StoragePathMacros.PROJECT_FILE)})
public class ProjectFileExclusionManagerImpl extends PersistentFileSetManager implements ProjectFileExclusionManager {

  
  private final Project myProject;

  public ProjectFileExclusionManagerImpl(Project project) {
    myProject = project;
  }

  public void addExclusion(VirtualFile file) {
    if (addFile(file)) {
      FileBasedIndex.getInstance().requestReindexExcluded(file);
      fireRootsChange(myProject);
    }
  }

  public void removeExclusion(VirtualFile file) {
    if (removeFile(file)) {
      FileBasedIndex.getInstance().requestReindex(file);
      fireRootsChange(myProject);
    }
  }

  private static void fireRootsChange(final Project project) {
    ApplicationManager.getApplication().runWriteAction(new Runnable(){
      @Override
      public void run() {
        ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.getInstance(), false, true);
      }
    });
  }

  @Override
  public boolean isExcluded(VirtualFile file) {
    return containsFile(file);
  }

  public Collection<VirtualFile> getExcludedFiles() {
    return getFiles();
  }


  public static ProjectFileExclusionManagerImpl getInstance(@NotNull Project project) {
    return (ProjectFileExclusionManagerImpl) ServiceManager.getService(project, ProjectFileExclusionManager.class);
  }

}
