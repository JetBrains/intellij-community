/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ide.impl;

import com.intellij.facet.*;
import com.intellij.ide.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.LibraryUtil;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WrappingVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;

/**
 * @author nik
 */
public class ProjectStructureSelectInTarget extends SelectInTargetBase implements SelectInTarget, DumbAware {
  @Override
  public boolean canSelect(final SelectInContext context) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(context.getProject()).getFileIndex();
    final VirtualFile file = context.getVirtualFile();
    if (file instanceof WrappingVirtualFile) {
      final Object o = ((WrappingVirtualFile)file).getWrappedObject(context.getProject());
      return o instanceof Facet;
    }
    return fileIndex.isInContent(file) || fileIndex.isInLibraryClasses(file) || fileIndex.isInLibrarySource(file)
           || StdFileTypes.IDEA_MODULE.equals(file.getFileType()) && findModuleByModuleFile(context.getProject(), file) != null;
  }

  @Override
  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    final Project project = context.getProject();
    final VirtualFile file = context.getVirtualFile();

    final Module module;
    final Facet facet;
    if (file instanceof WrappingVirtualFile) {
      final Object o = ((WrappingVirtualFile)file).getWrappedObject(project);
      facet = o instanceof Facet? (Facet)o : null;
      module = facet == null? null : facet.getModule();
    }
    else {
      Module moduleByIml = file.getFileType().equals(StdFileTypes.IDEA_MODULE) ? findModuleByModuleFile(project, file) : null;
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
      module = moduleByIml != null ? moduleByIml : fileIndex.getModuleForFile(file);
      facet = fileIndex.isInSourceContent(file) ? null : findFacet(project, file);
    }
    if (module != null || facet != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (facet != null) {
            ModulesConfigurator.showFacetSettingsDialog(facet, null);
          }
          else {
            ProjectSettingsService.getInstance(project).openModuleSettings(module);
          }
        }
      });
      return;
    }

    final OrderEntry orderEntry = LibraryUtil.findLibraryEntry(file, project);
    if (orderEntry != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          ProjectSettingsService.getInstance(project).openLibraryOrSdkSettings(orderEntry);
        }
      });
    }
  }

  @Nullable
  private static Module findModuleByModuleFile(@NotNull Project project, @NotNull VirtualFile file) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      if (ModuleUtilCore.isModuleFile(module, file)) {
        return module;
      }
    }
    return null;
  }

  @Nullable
  private static Facet findFacet(final @NotNull Project project, final @NotNull VirtualFile file) {
    for (FacetTypeId id : FacetTypeRegistry.getInstance().getFacetTypeIds()) {
      if (hasFacetWithRoots(project, id)) {
        Facet facet = FacetFinder.getInstance(project).findFacet(file, id);
        if (facet != null) {
          return facet;
        }
      }
    }
    return null;
  }

  private static <F extends Facet> boolean hasFacetWithRoots(final @NotNull Project project, final @NotNull FacetTypeId<F> id) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      Collection<? extends Facet> facets = FacetManager.getInstance(module).getFacetsByType(id);
      Iterator<? extends Facet> iterator = facets.iterator();
      if (iterator.hasNext()) {
        return iterator.next() instanceof FacetRootsProvider;
      }
    }
    return false;
  }

  public String toString() {
    return IdeBundle.message("select.in.project.settings");
  }

  @Override
  public float getWeight() {
    return StandardTargetWeights.PROJECT_SETTINGS_WEIGHT;
  }
}