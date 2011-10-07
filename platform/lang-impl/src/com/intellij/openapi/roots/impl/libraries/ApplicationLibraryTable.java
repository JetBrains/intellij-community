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

package com.intellij.openapi.roots.impl.libraries;

import com.intellij.ProjectTopics;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootListener;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablePresentation;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.util.Processor;
import com.intellij.util.containers.BidirectionalMultiMap;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 *  @author dsl
 */
@State(
  name = "libraryTable",
  roamingType = RoamingType.DISABLED,
  storages = {
    @Storage( file = "$OPTIONS$/applicationLibraries.xml")
    }
)
public class ApplicationLibraryTable extends LibraryTableBase implements ExportableComponent {
  private Set<String> myInitialLibraryNames = new HashSet<String>();
  private BidirectionalMultiMap<String, String> myProjectToUsedLibraries = new BidirectionalMultiMap<String, String>();

  private static final LibraryTablePresentation GLOBAL_LIBRARY_TABLE_PRESENTATION = new LibraryTablePresentation() {
    public String getDisplayName(boolean plural) {
      return ProjectBundle.message("global.library.display.name", plural ? 2 : 1);
    }

    public String getDescription() {
      return ProjectBundle.message("libraries.node.text.ide");
    }

    public String getLibraryTableEditorTitle() {
      return ProjectBundle.message("library.configure.global.title");
    }
  };

  public static ApplicationLibraryTable getApplicationTable() {
    return ServiceManager.getService(ApplicationLibraryTable.class);
  }

  public ApplicationLibraryTable(MessageBus messageBus) {
    messageBus.connect().subscribe(ProjectManager.TOPIC, new ProjectManagerAdapter() {
      @Override
      public void projectOpened(final Project project) {
        project.getMessageBus().connect().subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootListener() {
          @Override
          public void beforeRootsChange(ModuleRootEvent event) {
          }

          @Override
          public void rootsChanged(ModuleRootEvent event) {
            updateUsedLibraries(project);
          }
        });
      }
    });
  }

  private void updateUsedLibraries(Project project) {
    final String key = project.getProjectFilePath();
    myProjectToUsedLibraries.removeKey(key);
    ProjectRootManager.getInstance(project).orderEntries().forEachLibrary(new Processor<Library>() {
      @Override
      public boolean process(Library library) {
        final LibraryTable table = library.getTable();
        if (table != null && LibraryTablesRegistrar.APPLICATION_LEVEL.equals(table.getTableLevel())) {
          myProjectToUsedLibraries.put(key, library.getName());
        }
        return true;
      }
    });
  }

  public String getTableLevel() {
    return LibraryTablesRegistrar.APPLICATION_LEVEL;
  }

  public LibraryTablePresentation getPresentation() {
    return GLOBAL_LIBRARY_TABLE_PRESENTATION;
  }

  public boolean isEditable() {
    return true;
  }

  public boolean isUsedInOtherProjects(@NotNull Library library, @NotNull Project project) {
    if (myInitialLibraryNames.contains(library.getName())) {
      return true;
    }
    final Set<String> keys = myProjectToUsedLibraries.getKeys(library.getName());
    return keys != null && (keys.size() > 1 || !keys.contains(project.getProjectFilePath()));
  }

  @Override
  protected void onLibrariesLoaded() {
    for (Library library : getLibraries()) {
      myInitialLibraryNames.add(library.getName());
    }
  }

  public static String getExternalFileName() {
    return "applicationLibraries";
  }

  @NotNull
  public File[] getExportFiles() {
    return new File[]{PathManager.getOptionsFile(getExternalFileName())};
  }

  @NotNull
  public String getPresentableName() {
    return ProjectBundle.message("library.global.settings");
  }
}
