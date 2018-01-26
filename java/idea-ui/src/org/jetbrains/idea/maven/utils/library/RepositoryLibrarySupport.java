/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.maven.utils.library;

import com.google.common.collect.Iterables;
import com.intellij.jarRepository.RepositoryLibraryType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.ui.configuration.libraries.LibraryEditingUtil;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

import java.util.Arrays;

public class RepositoryLibrarySupport {
  @NotNull private Project project;
  @NotNull private RepositoryLibraryPropertiesModel model;
  @NotNull private RepositoryLibraryDescription libraryDescription;

  public RepositoryLibrarySupport(@NotNull Project project,
                                  @NotNull RepositoryLibraryDescription libraryDescription,
                                  @NotNull RepositoryLibraryPropertiesModel model) {
    this.project = project;
    this.libraryDescription = libraryDescription;
    this.model = model;
  }

  public void addSupport(@NotNull Module module,
                         @NotNull final ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider) {
    LibraryTable.ModifiableModel modifiableModel = modifiableModelsProvider.getLibraryTableModifiableModel(module.getProject());

    Library library = Iterables.find(Arrays.asList(modifiableModel.getLibraries()), library1 -> isLibraryEqualsToSelected(library1), null);
    if (library == null) {
      library = createNewLibrary(module, modifiableModel);
    }
    else {
      modifiableModelsProvider.disposeLibraryTableModifiableModel(modifiableModel);
    }
    final DependencyScope dependencyScope = LibraryDependencyScopeSuggester.getDefaultScope(library);
    final ModifiableRootModel moduleModifiableModel = modifiableModelsProvider.getModuleModifiableModel(module);
    LibraryOrderEntry foundEntry =
      (LibraryOrderEntry)Iterables.find(Arrays.asList(moduleModifiableModel.getOrderEntries()), entry -> entry instanceof LibraryOrderEntry
                                                                                                     && ((LibraryOrderEntry)entry).getScope() == dependencyScope
                                                                                                     && isLibraryEqualsToSelected(((LibraryOrderEntry)entry).getLibrary()), null);
    modifiableModelsProvider.disposeModuleModifiableModel(moduleModifiableModel);
    if (foundEntry == null) {
      rootModel.addLibraryEntry(library).setScope(dependencyScope);
    }
  }

  private LibraryEx createNewLibrary(@NotNull final Module module, final LibraryTable.ModifiableModel modifiableModel) {
    RepositoryLibraryProperties libraryProperties = new RepositoryLibraryProperties(
      libraryDescription.getGroupId(),
      libraryDescription.getArtifactId(),
      model.getVersion(),
      model.isIncludeTransitiveDependencies());
    final LibraryEx library = (LibraryEx)modifiableModel.createLibrary(
      LibraryEditingUtil.suggestNewLibraryName(modifiableModel, RepositoryLibraryType.getInstance().getDescription(libraryProperties)),
      RepositoryLibraryType.REPOSITORY_LIBRARY_KIND);
    RepositoryLibraryProperties realLibraryProperties = (RepositoryLibraryProperties)library.getProperties();
    realLibraryProperties.setMavenId(libraryProperties.getMavenId());

    ApplicationManager.getApplication().runWriteAction(() -> modifiableModel.commit());
    RepositoryUtils.loadDependencies(
      module.getProject(),
      library,
      model.isDownloadSources(),
      model.isDownloadJavaDocs(),
      null);
    return library;
  }

  private boolean isLibraryEqualsToSelected(Library library) {
    if (!(library instanceof LibraryEx)) {
      return false;
    }

    LibraryEx libraryEx = (LibraryEx)library;
    if (!RepositoryLibraryType.REPOSITORY_LIBRARY_KIND.equals(libraryEx.getKind())) {
      return false;
    }

    LibraryProperties libraryProperties = libraryEx.getProperties();
    if (!(libraryProperties instanceof RepositoryLibraryProperties)) {
      return false;
    }
    RepositoryLibraryProperties repositoryLibraryProperties = (RepositoryLibraryProperties)libraryProperties;
    RepositoryLibraryDescription description = RepositoryLibraryDescription.findDescription(repositoryLibraryProperties);

    if (!description.equals(libraryDescription)) {
      return false;
    }

    return Comparing.equal(repositoryLibraryProperties.getVersion(), model.getVersion());
  }
}
