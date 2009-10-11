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
package com.intellij.ide.impl;

import com.intellij.facet.*;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInTarget;
import com.intellij.ide.StandardTargetWeights;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ui.configuration.ModulesConfigurator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.WrappingVirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

/**
 * @author nik
 */
public class ProjectSettingsSelectInTarget implements SelectInTarget, DumbAware {
  public boolean canSelect(final SelectInContext context) {
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(context.getProject()).getFileIndex();
    final VirtualFile file = context.getVirtualFile();
    if (file instanceof WrappingVirtualFile) {
      final Object o = ((WrappingVirtualFile)file).getWrappedObject(context.getProject());
      return o instanceof Facet;
    }
    return fileIndex.isInContent(file) || fileIndex.isInLibraryClasses(file) || fileIndex.isInLibrarySource(file);
  }

  public void selectIn(final SelectInContext context, final boolean requestFocus) {
    final Project project = context.getProject();
    final VirtualFile file = context.getVirtualFile();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    final Module module;
    final Facet facet;
    if (file instanceof WrappingVirtualFile) {
      final Object o = ((WrappingVirtualFile)file).getWrappedObject(project);
      facet = o instanceof Facet? (Facet)o : null;
      module = facet == null? null : facet.getModule();
    }
    else {
      module = fileIndex.getModuleForFile(file);
      facet = findFacet(project, file, fileIndex);
    }
    if (module != null || facet != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          if (facet != null) {
            ModulesConfigurator.showFacetSettingsDialog(facet, null);
          }
          else {
            ModulesConfigurator.showDialog(project, module.getName(), null, false);
          }
        }
      });
      return;
    }

    final LibraryOrderEntry libraryOrderEntry = findLibrary(file, fileIndex);
    if (libraryOrderEntry != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          ModulesConfigurator.showLibrarySettings(project, libraryOrderEntry);
        }
      });
      return;
    }

    final Sdk jdk = findJdk(file, fileIndex);
    if (jdk != null) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        public void run() {
          ModulesConfigurator.showSdkSettings(project, jdk);
        }
      });
    }
  }

  @Nullable
  private static LibraryOrderEntry findLibrary(final VirtualFile file, final ProjectFileIndex fileIndex) {
    List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(file);
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        return (LibraryOrderEntry)entry;
      }
    }
    return null;
  }

  @Nullable
  private static Sdk findJdk(final VirtualFile file, final ProjectFileIndex fileIndex) {
    List<OrderEntry> entries = fileIndex.getOrderEntriesForFile(file);
    for (OrderEntry entry : entries) {
      if (entry instanceof JdkOrderEntry) {
        return ((JdkOrderEntry)entry).getJdk();
      }
    }
    return null;
  }

  @Nullable
  private static Facet findFacet(final @NotNull Project project, final @NotNull VirtualFile file, final @NotNull ProjectFileIndex fileIndex) {
    if (!fileIndex.isInSourceContent(file)) {
      for (FacetTypeId id : FacetTypeRegistry.getInstance().getFacetTypeIds()) {
        if (hasFacetWithRoots(project, id)) {
          Facet facet = FacetFinder.getInstance(project).findFacet(file, id);
          if (facet != null) {
            return facet;
          }
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

  public String getToolWindowId() {
    return null;
  }

  public String getMinorViewId() {
    return null;
  }

  public String toString() {
    return IdeBundle.message("select.in.project.settings");
  }

  public float getWeight() {
    return StandardTargetWeights.PROJECT_SETTINGS_WEIGHT;
  }
}
