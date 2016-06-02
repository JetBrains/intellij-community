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
package com.jetbrains.jsonSchema;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.FileContentUtilCore;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Irina.Chernushina on 5/30/2016.
 */
public class JsonSchemaFileTypeManager implements ProjectManagerListener {
  private final Map<Project, Collection<VirtualFile>> myFileSets = new ConcurrentHashMap<Project, Collection<VirtualFile>>();
  private volatile boolean mySetsInitialized;
  private static final Object LOCK = new Object();

  public static JsonSchemaFileTypeManager getInstance() {
    return ServiceManager.getService(JsonSchemaFileTypeManager.class);
  }

  public boolean isJsonSchemaFile(@NotNull final VirtualFile file) {
    ensureInitialized();
    for (Collection<VirtualFile> files : myFileSets.values()) {
      if (files.contains(file)) return true;
    }
    return false;
  }

  private void ensureInitialized() {
    if (mySetsInitialized) return;
    synchronized (LOCK) {
      if (mySetsInitialized) return;
      Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      for (Project openProject : openProjects) {
        addProjectFiles(openProject);
      }
      mySetsInitialized = true;
    }
  }

  private Set<VirtualFile> addProjectFiles(@NotNull final Project project) {
    final Set<VirtualFile> files = JsonSchemaService.Impl.getEx(project).getSchemaFiles();
    myFileSets.put(project, files);
    return files;
  }

  @Override
  public void projectOpened(Project project) {
    reparseFiles(addProjectFiles(project));
  }

  public void reset(@NotNull final Project project) {
    final Collection<VirtualFile> files = myFileSets.remove(project);
    reparseFiles(files);
  }

  private static void reparseFiles(Collection<VirtualFile> files) {
    if (files != null && !files.isEmpty()) {
      FileContentUtilCore.reparseFiles(files);
    }
  }

  @Override
  public boolean canCloseProject(Project project) {
    return true;
  }

  @Override
  public void projectClosed(Project project) {
    myFileSets.remove(project);
  }

  @Override
  public void projectClosing(Project project) {
  }
}
