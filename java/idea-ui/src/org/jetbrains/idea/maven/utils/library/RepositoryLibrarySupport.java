// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.utils.library.propertiesEditor.RepositoryLibraryPropertiesModel;

import java.util.Arrays;
import java.util.Objects;

public class RepositoryLibrarySupport {
  @NotNull private final Project project;
  @NotNull private final RepositoryLibraryPropertiesModel model;
  @NotNull private final RepositoryLibraryDescription libraryDescription;

  public RepositoryLibrarySupport(@NotNull Project project,
                                  @NotNull RepositoryLibraryDescription libraryDescription,
                                  @NotNull RepositoryLibraryPropertiesModel model) {
    this.project = project;
    this.libraryDescription = libraryDescription;
    this.model = model;
  }

  /**
   * @deprecated Use {@link #addSupport(Module, ModifiableRootModel, ModifiableModelsProvider, DependencyScope)}
   */
  @Deprecated(forRemoval = true)
  public void addSupport(@NotNull Module module,
                         @NotNull final ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider) {
    addSupport(module, rootModel, modifiableModelsProvider, null);
  }

  public void addSupport(@NotNull Module module,
                         @NotNull final ModifiableRootModel rootModel,
                         @NotNull ModifiableModelsProvider modifiableModelsProvider,
                         @Nullable DependencyScope scope) {
    LibraryTable.ModifiableModel modifiableModel = modifiableModelsProvider.getLibraryTableModifiableModel(module.getProject());

    Library library = Iterables.find(Arrays.asList(modifiableModel.getLibraries()), library1 -> isLibraryEqualsToSelected(library1), null);
    if (library == null) {
      library = createNewLibrary(module, modifiableModel);
    }
    else {
      modifiableModelsProvider.disposeLibraryTableModifiableModel(modifiableModel);
    }
    final DependencyScope dependencyScope = scope != null ? scope : LibraryDependencyScopeSuggester.getDefaultScope(library);
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
      model.isIncludeTransitiveDependencies(),
      model.getExcludedDependencies());
    final LibraryEx library = (LibraryEx)modifiableModel.createLibrary(
      LibraryEditingUtil.suggestNewLibraryName(modifiableModel, RepositoryLibraryType.getInstance().getDescription(libraryProperties)),
      RepositoryLibraryType.REPOSITORY_LIBRARY_KIND);

    LibraryEx.ModifiableModelEx modifiableLibrary = library.getModifiableModel();

    RepositoryLibraryProperties realLibraryProperties = (RepositoryLibraryProperties)modifiableLibrary.getProperties();
    realLibraryProperties.setMavenId(libraryProperties.getMavenId());
    modifiableLibrary.setProperties(realLibraryProperties);

    ApplicationManager.getApplication().runWriteAction(() -> {
      modifiableLibrary.commit();
      modifiableModel.commit();
    });
    RepositoryUtils.loadDependenciesToLibrary(
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

    return Objects.equals(repositoryLibraryProperties.getVersion(), model.getVersion());
  }
}
