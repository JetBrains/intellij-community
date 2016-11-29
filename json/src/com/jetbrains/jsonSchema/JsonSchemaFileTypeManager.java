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
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author Irina.Chernushina on 5/30/2016.
 */
public class JsonSchemaFileTypeManager implements ProjectManagerListener {
  private final Collection<VirtualFile> myFileSets = ContainerUtil.createConcurrentList();
  private volatile boolean mySetsInitialized;
  private static final Object LOCK = new Object();

  public static JsonSchemaFileTypeManager getInstance() {
    return ServiceManager.getService(JsonSchemaFileTypeManager.class);
  }

  public JsonSchemaFileTypeManager() {
    ProjectManager.getInstance().addProjectManagerListener(this);
  }

  public boolean isJsonSchemaFile(@NotNull final VirtualFile file) {
    ensureInitialized();
    return myFileSets.contains(file);
  }

  private void ensureInitialized() {
    if (mySetsInitialized) return;
    synchronized (LOCK) {
      if (mySetsInitialized) return;
      myFileSets.clear();
      final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      for (Project openProject : openProjects) {
        myFileSets.addAll(JsonSchemaService.Impl.getEx(openProject).getSchemaFiles());
      }
      mySetsInitialized = true;
    }
  }

  @Override
  public void projectOpened(Project project) {
    reset();
  }

  public void reset() {
    mySetsInitialized = false;
    ensureInitialized();
  }

  @Override
  public void projectClosed(Project project) {
    reset();
  }
}
