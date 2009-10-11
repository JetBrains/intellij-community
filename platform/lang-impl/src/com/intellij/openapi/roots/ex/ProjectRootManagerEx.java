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

package com.intellij.openapi.roots.ex;

import com.intellij.ide.startup.CacheUpdater;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.JdkOrderEntry;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.search.GlobalSearchScope;

import java.util.EventListener;
import java.util.List;

public abstract class ProjectRootManagerEx extends ProjectRootManager {
  public static ProjectRootManagerEx getInstanceEx(Project project) {
    return (ProjectRootManagerEx)getInstance(project);
  }

  public abstract void registerChangeUpdater(CacheUpdater updater);

  public abstract void unregisterChangeUpdater(CacheUpdater updater);

  public abstract void addProjectJdkListener(ProjectJdkListener listener);

  public abstract void removeProjectJdkListener(ProjectJdkListener listener);

  public abstract void beforeRootsChange(boolean filetypes);

  public abstract void rootsChanged(boolean filetypes);

  public abstract void mergeRootsChangesDuring(Runnable r);

  public abstract GlobalSearchScope getScopeForLibraryUsedIn(List<Module> modulesLibraryIsUsedIn);

  public abstract GlobalSearchScope getScopeForJdk(final JdkOrderEntry jdkOrderEntry);

  public abstract void clearScopesCachesForModules();


  public interface ProjectJdkListener extends EventListener {
    void projectJdkChanged();
  }
}
