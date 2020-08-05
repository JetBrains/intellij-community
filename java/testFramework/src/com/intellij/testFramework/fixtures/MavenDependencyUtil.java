// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.util.Collection;
import java.util.List;

public final class MavenDependencyUtil {
  /**
   * Adds a Maven library to given model as {@link DependencyScope#COMPILE} dependency including transitive dependencies.
   *
   * @param model            root model to add a Maven library to
   * @param mavenCoordinates maven coordinates like groupID:artifactID:version
   */
  public static void addFromMaven(@NotNull ModifiableRootModel model, String mavenCoordinates) {
    addFromMaven(model, mavenCoordinates, true);
  }

  /**
   * Adds a Maven library to given model as {@link DependencyScope#COMPILE} dependency.
   *
   * @param model                         root model to add a Maven library to
   * @param mavenCoordinates              maven coordinates like groupID:artifactID:version
   * @param includeTransitiveDependencies true for include transitive dependencies, false otherwise
   */
  public static void addFromMaven(@NotNull ModifiableRootModel model, String mavenCoordinates, boolean includeTransitiveDependencies) {
    addFromMaven(model, mavenCoordinates, includeTransitiveDependencies, DependencyScope.COMPILE);
  }

  /**
   * Adds a Maven library to given model.
   *
   * @param model                         root model to add a Maven library to
   * @param mavenCoordinates              maven coordinates like groupID:artifactID:version
   * @param includeTransitiveDependencies true for include transitive dependencies, false otherwise
   * @param dependencyScope               scope of the library
   */
  public static void addFromMaven(@NotNull ModifiableRootModel model, String mavenCoordinates,
                                  boolean includeTransitiveDependencies, DependencyScope dependencyScope) {
    List<RemoteRepositoryDescription> remoteRepositoryDescriptions = getRemoteRepositoryDescriptions();
    RepositoryLibraryProperties libraryProperties = new RepositoryLibraryProperties(mavenCoordinates, includeTransitiveDependencies);
    Collection<OrderRoot> roots =
      JarRepositoryManager.loadDependenciesModal(model.getProject(), libraryProperties, false, false, null, remoteRepositoryDescriptions);
    LibraryTable.ModifiableModel tableModel = model.getModuleLibraryTable().getModifiableModel();
    Library library = tableModel.createLibrary(mavenCoordinates);
    Library.ModifiableModel libraryModel = library.getModifiableModel();
    if (roots.isEmpty()) {
      throw new IllegalStateException(String.format("No roots for '%s'", mavenCoordinates));
    }

    for (OrderRoot root : roots) {
      libraryModel.addRoot(root.getFile(), root.getType());
    }

    LibraryOrderEntry libraryOrderEntry = model.findLibraryOrderEntry(library);
    if (libraryOrderEntry == null) {
      throw new IllegalStateException("Unable to find registered library " + mavenCoordinates);
    }
    libraryOrderEntry.setScope(dependencyScope);

    libraryModel.commit();
    tableModel.commit();
  }

  @NotNull
  private static List<RemoteRepositoryDescription> getRemoteRepositoryDescriptions() {
    return ContainerUtil.map(IntelliJProjectConfiguration.getRemoteRepositoryDescriptions(), repository ->
      new RemoteRepositoryDescription(repository.getId(), repository.getName(), repository.getUrl()));
  }
}
