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
import com.intellij.util.ui.UIUtil;
import com.jetbrains.jsonSchema.ide.JsonSchemaService;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Irina.Chernushina on 5/30/2016.
 */
public class JsonSchemaFileTypeManager implements ProjectManagerListener {
  private final Set<VirtualFile> myFileSets = Collections.synchronizedSet(new HashSet<>());
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
      final Set<VirtualFile> copy = new HashSet<>();
      copy.addAll(myFileSets);
      myFileSets.clear();
      final Project[] openProjects = ProjectManager.getInstance().getOpenProjects();
      for (Project openProject : openProjects) {
        myFileSets.addAll(JsonSchemaService.Impl.getEx(openProject).getSchemaFiles());
      }
      final Set<VirtualFile> toRefresh = symmetricDifference(copy, myFileSets);
      UIUtil.invokeLaterIfNeeded(() -> FileContentUtilCore.reparseFiles(toRefresh));
      mySetsInitialized = true;
    }
  }

  private static <T> Set<T> symmetricDifference(Set<T> a, Set<T> b) {
    final Set<T> result = new HashSet<T>(a);
    for (T element : b) {
      // .add() returns false if element already exists
      if (!result.add(element)) {
        result.remove(element);
      }
    }
    return result;
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
  public boolean canCloseProject(Project project) {
    return true;
  }

  @Override
  public void projectClosed(Project project) {
    reset();
  }

  @Override
  public void projectClosing(Project project) {
  }
}
