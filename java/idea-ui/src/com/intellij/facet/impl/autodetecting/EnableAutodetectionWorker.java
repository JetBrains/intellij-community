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
package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.FacetType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.util.MultiValuesMap;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.*;

/**
 * @author nik
 */
public class EnableAutodetectionWorker {
  private final MultiValuesMap<FacetType<?,?>, Module> myModulesToProcess = new MultiValuesMap<FacetType<?,?>, Module>();
  private final MultiValuesMap<FacetType<?,?>, VirtualFile> myFilesToProcess = new MultiValuesMap<FacetType<?,?>, VirtualFile>();
  private final Project myProject;
  private final FacetAutodetectingManagerImpl myFacetAutodetectingManager;

  public EnableAutodetectionWorker(@NotNull Project project, final FacetAutodetectingManagerImpl facetAutodetectingManager) {
    myProject = project;
    myFacetAutodetectingManager = facetAutodetectingManager;
  }

  public void addFile(@NotNull FacetType<?, ?> type, @NotNull String url) {
    VirtualFile file = VirtualFileManager.getInstance().findFileByUrl(url);
    if (file != null) {
      myFilesToProcess.put(type, file);
    }
  }

  public void queueChanges(final @NotNull FacetType<?, ?> facetType, final @Nullable DisabledAutodetectionByTypeElement oldElement,
                           final @Nullable DisabledAutodetectionByTypeElement newElement) {
    if (oldElement == null || newElement != null && newElement.getModuleElements().isEmpty()) return;

    ModuleManager moduleManager = ModuleManager.getInstance(myProject);
    List<Module> modulesToProcess = new ArrayList<Module>();

    List<DisabledAutodetectionInModuleElement> moduleElements = oldElement.getModuleElements();
    for (DisabledAutodetectionInModuleElement moduleElement : moduleElements) {
      if (moduleElement.isDisableInWholeModule()) {
        Module module = moduleManager.findModuleByName(moduleElement.getModuleName());
        if (module != null) {
          modulesToProcess.add(module);
        }
      }
      else {
        for (String url : moduleElement.getFiles()) {
          if (newElement == null || !newElement.isDisabled(moduleElement.getModuleName(), url)) {
            addFile(facetType, url);
          }
        }
        for (String directoryUrl : moduleElement.getDirectories()) {
          if (newElement == null || !newElement.isDisabled(moduleElement.getModuleName(), directoryUrl)) {
            addFile(facetType, directoryUrl);
          }
        }
      }
    }

    if (moduleElements.isEmpty()) {
      ContainerUtil.addAll(modulesToProcess, moduleManager.getModules());
    }
    if (newElement != null) {
      Set<String> toRemove = new THashSet<String>();
      for (DisabledAutodetectionInModuleElement moduleElement : newElement.getModuleElements()) {
        if (moduleElement.isDisableInWholeModule()) {
          toRemove.add(moduleElement.getModuleName());
        }
      }

      Iterator<Module> iterator = modulesToProcess.iterator();
      while (iterator.hasNext()) {
        Module module = iterator.next();
        if (toRemove.contains(module.getName())) {
          iterator.remove();
        }
      }
    }

    if (!modulesToProcess.isEmpty()) {
      myModulesToProcess.putAll(facetType, modulesToProcess);
    }
  }

  public void redetectFacets() {
    ProgressManager.getInstance().runProcessWithProgressSynchronously(new Runnable() {
      public void run() {
        for (FacetType<?, ?> type : myModulesToProcess.keySet()) {
          detectFacetsInModules(type, myModulesToProcess.get(type));
        }
        myModulesToProcess.clear();

        for (FacetType<?, ?> type : myFilesToProcess.keySet()) {
          detectFacetsInFiles(type, myFilesToProcess.get(type));
        }
        myFilesToProcess.clear();
      }

    }, ProjectBundle.message("progress.text.detecting.facets"), false, myProject);
    myFacetAutodetectingManager.getDetectedFacetManager().showDetectedFacetsDialog();
  }

  private void detectFacetsInModules(final FacetType<?, ?> type, final Collection<Module> modules) {
    for (Module module : modules) {
      ModuleRootManager.getInstance(module).getFileIndex().iterateContent(new ContentIterator() {
        public boolean processFile(final VirtualFile file) {
          detectFacetsInFile(type, file);
          return true;
        }
      });
    }
  }

  private void detectFacetsInFile(final FacetType<?, ?> type, final VirtualFile file) {
    //todo[nik] detect only facets of specified type
    if (file.isDirectory()) {
      for (VirtualFile child : file.getChildren()) {
        detectFacetsInFile(type, child);
      }
    }
    else {
      myFacetAutodetectingManager.processFile(file);
    }
  }

  private void detectFacetsInFiles(final FacetType<?, ?> type, final Collection<VirtualFile> virtualFiles) {
    for (VirtualFile file : virtualFiles) {
      detectFacetsInFile(type, file);
    }
  }

  @TestOnly
  @Nullable
  public Collection<Module> getModulesToProcess(FacetType<?, ?> facetType) {
    return myModulesToProcess.get(facetType);
  }

  @TestOnly
  @Nullable
  public Collection<VirtualFile> getFilesToProcess(FacetType<?, ?> facetType) {
    return myFilesToProcess.get(facetType);
  }
}
