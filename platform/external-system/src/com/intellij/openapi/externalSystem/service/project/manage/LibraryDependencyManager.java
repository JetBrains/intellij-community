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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.externalSystem.model.DataHolder;
import com.intellij.openapi.externalSystem.model.ExternalSystemProjectKeys;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.service.project.PlatformFacade;
import com.intellij.openapi.externalSystem.service.project.ProjectStructureHelper;
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 4/12/13 6:19 PM
 */
public class LibraryDependencyManager implements ProjectDataManager<LibraryDependencyData> {
  
  @NotNull private final PlatformFacade myPlatformFacade;
  @NotNull private final LibraryDataManager myLibraryManager;

  public LibraryDependencyManager(@NotNull PlatformFacade platformFacade, @NotNull LibraryDataManager manager) {
    myPlatformFacade = platformFacade;
    myLibraryManager = manager;
  }

  @NotNull
  @Override
  public Key<LibraryDependencyData> getTargetDataKey() {
    return ExternalSystemProjectKeys.LIBRARY_DEPENDENCY;
  }

  @Override
  public void importData(@NotNull Collection<DataHolder<LibraryDependencyData>> datas, @NotNull Project project, boolean synchronous) {
    // TODO den implement 
  }

  private void doImportData(@NotNull final Collection<DataHolder<LibraryDependencyData>> datas,
                            @NotNull ProjectSystemId externalSystemId,
                            @NotNull Project project,
                            @NotNull final Module module,
                            final boolean synchronous)
  {
    ExternalSystemUtil.executeProjectChangeAction(project, externalSystemId, datas, synchronous, new Runnable() {
      @Override
      public void run() {
        LibraryTable libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
        Set<LibraryData> librariesToImport = new HashSet<LibraryData>();
        for (DataHolder<LibraryDependencyData> dataHolder : datas) {
          LibraryDependencyData data = dataHolder.getData();
          final Library library = libraryTable.getLibraryByName(data.getName());
          if (library == null) {
            librariesToImport.add(data.getTarget());
          }
        }
        if (!librariesToImport.isEmpty()) {
          myLibraryManager.importLibraries(librariesToImport, module.getProject(), synchronous);
        }

        for (DataHolder<LibraryDependencyData> dataHolder : datas) {
          ProjectStructureHelper helper = ServiceManager.getService(module.getProject(), ProjectStructureHelper.class);
          ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
          final ModifiableRootModel moduleRootModel = moduleRootManager.getModifiableModel();
          try {
            libraryTable = myPlatformFacade.getProjectLibraryTable(module.getProject());
            final Library library = libraryTable.getLibraryByName(dataHolder.getName());
            if (library == null) {
              assert false;
              continue;
            }
            LibraryOrderEntry orderEntry = helper.findIdeLibraryDependency(dataHolder.getName(), moduleRootModel);
            if (orderEntry == null) {
              // We need to get the most up-to-date Library object due to our project model restrictions.
              orderEntry = moduleRootModel.addLibraryEntry(library);
            }
            orderEntry.setExported(dataHolder.isExported());
            orderEntry.setScope(dataHolder.getScope());
          }
          finally {
            moduleRootModel.commit();
          }
        }
      }
    })
  }

  @Override
  public void removeData(@NotNull Collection<DataHolder<LibraryDependencyData>> datas, @NotNull Project project, boolean synchronous) {
    // TODO den implement 
  }
}
