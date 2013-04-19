/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.openapi.externalSystem.service.project.manage;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.CompositeLibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureServices;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 1/23/13 11:35 AM
 */
public class OutdatedLibraryService {

  @NotNull private final LibraryDataService           myLibraryManager;
  @NotNull private final LibraryDependencyDataService myLibraryDependencyManager;
  @NotNull private final ProjectStructureServices     myContext;
  @NotNull private final Project                      myProject;

  public OutdatedLibraryService(@NotNull LibraryDataService libraryManager,
                                @NotNull LibraryDependencyDataService libraryDependencyManager,
                                @NotNull ProjectStructureServices context,
                                @NotNull Project project)
  {
    myLibraryManager = libraryManager;
    myLibraryDependencyManager = libraryDependencyManager;
    myContext = context;
    myProject = project;
  }

  public void sync(@NotNull Project project,
                   @NotNull ProjectSystemId externalSystemId,
                   @NotNull Collection<ProjectStructureNode<?>> nodes)
  {
    List<Pair<LibraryDependencyData, Module>> libraryDependenciesToImport = ContainerUtilRt.newArrayList();
    Map<String /* ide library name */, Library> ideLibsToRemove = ContainerUtilRt.newHashMap();
    Map<String /* ide library name */, LibraryData> ide2externalLibs = ContainerUtilRt.newHashMap();
    Collection<LibraryOrderEntry> ideLibraryDependenciesToRemove = ContainerUtilRt.newArrayList();
    ProjectStructureHelper projectStructureHelper = myContext.getProjectStructureHelper();
    PlatformFacade facade = myContext.getPlatformFacade();

    // The general idea is to remove all ide-local entities references by the 'outdated libraries' and import
    // all corresponding external-local entities.

    //region Parse information to use for further processing.
    for (ProjectStructureNode<?> node : nodes) {
      Object entity = node.getDescriptor().getElement().mapToEntity(myContext, project);
      if (!(entity instanceof CompositeLibraryDependencyData)) {
        continue;
      }
      CompositeLibraryDependencyData e = (CompositeLibraryDependencyData)entity;
      String ideLibraryName = e.getIdeEntity().getLibraryName();
      Library ideLibraryToRemove = null;
      if (ideLibraryName != null) {

        ideLibraryToRemove = projectStructureHelper.findIdeLibrary(ideLibraryName, project);
      }

      if (ideLibraryToRemove != null) {
        // We use map here because Library.hashCode()/equals() contract is not clear. That's why we consider two currently
        // configured libraries with the same name to be the same.
        ideLibsToRemove.put(ideLibraryName, ideLibraryToRemove);
        ide2externalLibs.put(ideLibraryName, e.getExternalEntity().getTarget());
      }
    }
    //endregion

    //region Do actual sync
    RootPolicy<LibraryOrderEntry> visitor = new RootPolicy<LibraryOrderEntry>() {
      @Override
      public LibraryOrderEntry visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, LibraryOrderEntry value) {
        return libraryOrderEntry;
      }
    };
    for (Module ideModule : facade.getModules(myProject)) {
      DataNode<ModuleData> externalModule = projectStructureHelper.findExternalModule(ideModule.getName(), externalSystemId, project);
      if (externalModule == null) {
        continue;
      }

      for (OrderEntry entry : facade.getOrderEntries(ideModule)) {
        LibraryOrderEntry ideLibraryDependency = entry.accept(visitor, null);
        if (ideLibraryDependency == null) {
          continue;
        }
        String libraryName = ideLibraryDependency.getLibraryName();
        if (libraryName == null) {
          continue;
        }
        if (!ideLibsToRemove.containsKey(libraryName)) {
          continue;
        }
        ideLibraryDependenciesToRemove.add(ideLibraryDependency);
        // TODO den implement
//        LibraryDependencyData externalLibraryDependency = new LibraryDependencyData(externalModule, ide2externalLibs.get(libraryName));
//        externalLibraryDependency.setExported(ideLibraryDependency.isExported());
//        externalLibraryDependency.setScope(ideLibraryDependency.getScope());
//        libraryDependenciesToImport.add(Pair.create(externalLibraryDependency, ideModule));
      }
    }
    // TODO den implement
//    myLibraryDependencyManager.removeData(ideLibraryDependenciesToRemove, true);
//    myLibraryManager.removeLibraries(ideLibsToRemove.values(), myProject);
//    for (Pair<LibraryDependencyData, Module> pair : libraryDependenciesToImport) {
//      // Assuming that dependency manager is smart enough to import library for a given library dependency if it hasn't been
//      // imported yet.
//      myLibraryDependencyManager.importData(pair.first, externalSystemId, pair.second, true);
//    }
    //endregion
  }
}
