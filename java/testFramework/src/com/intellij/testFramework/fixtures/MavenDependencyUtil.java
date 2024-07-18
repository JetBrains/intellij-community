// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RemoteRepositoryDescription;
import com.intellij.jarRepository.RepositoryLibraryType;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.ui.OrderRoot;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;

import java.util.Collection;
import java.util.Collections;
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
  public static void addFromMaven(@NotNull ModifiableRootModel model,
                                  String mavenCoordinates,
                                  boolean includeTransitiveDependencies,
                                  DependencyScope dependencyScope) {
    addFromMaven(model, mavenCoordinates, includeTransitiveDependencies, dependencyScope, Collections.emptyList());
  }

  /**
   * Adds a Maven library with the default packaging (JAR) to {@code model}
   * @param additionalRepositories additional Maven repositories where the artifacts should be searched (in addition to repositories
   *                               configured in intellij project)
   */
  public static void addFromMaven(@NotNull ModifiableRootModel model,
                                  String mavenCoordinates,
                                  boolean includeTransitiveDependencies,
                                  DependencyScope dependencyScope,
                                  List<RemoteRepositoryDescription> additionalRepositories) {
    addFromMaven(model, mavenCoordinates, includeTransitiveDependencies, dependencyScope, additionalRepositories,
                 JpsMavenRepositoryLibraryDescriptor.DEFAULT_PACKAGING);
  }

  /**
   * Adds a Maven library to {@code model}
   * @param packaging artifact packaging matching {@link org.jetbrains.idea.maven.aether.ArtifactKind} of the requested library
   */
  public static void addFromMaven(@NotNull ModifiableRootModel model,
                                  String mavenCoordinates,
                                  boolean includeTransitiveDependencies,
                                  DependencyScope dependencyScope,
                                  List<RemoteRepositoryDescription> additionalRepositories,
                                  @NotNull String packaging) {
    List<RemoteRepositoryDescription> remoteRepositoryDescriptions = ContainerUtil.concat(getRemoteRepositoryDescriptions(),
                                                                                          additionalRepositories);
    RepositoryLibraryProperties libraryProperties =
      new RepositoryLibraryProperties(mavenCoordinates, packaging, includeTransitiveDependencies);
    Collection<OrderRoot> roots =
      JarRepositoryManager.loadDependenciesModal(model.getProject(), libraryProperties, false, false, null, remoteRepositoryDescriptions);
    LibraryTable.ModifiableModel tableModel = model.getModuleLibraryTable().getModifiableModel();
    Library library = tableModel.createLibrary(mavenCoordinates, RepositoryLibraryType.REPOSITORY_LIBRARY_KIND);
    Library.ModifiableModel libraryModel = library.getModifiableModel();
    if (roots.isEmpty()) {
      throw new IllegalStateException(String.format("No roots for '%s'", mavenCoordinates));
    }

    for (OrderRoot root : roots) {
      libraryModel.addRoot(root.getFile(), root.getType());
    }
    ((LibraryEx.ModifiableModelEx) libraryModel).setProperties(libraryProperties);

    LibraryOrderEntry libraryOrderEntry = model.findLibraryOrderEntry(library);
    if (libraryOrderEntry == null) {
      throw new IllegalStateException("Unable to find registered library " + mavenCoordinates);
    }
    libraryOrderEntry.setScope(dependencyScope);

    libraryModel.commit();
    tableModel.commit();
  }
  
  private static final List<RemoteRepositoryDescription> REPOS_FOR_TESTING = List.of(
    new RemoteRepositoryDescription("central-proxy", "Maven Central Proxy", 
                                    "https://cache-redirector.jetbrains.com/repo1.maven.org/maven2"),
    new RemoteRepositoryDescription("intellij-dependencies", "IntelliJ Dependencies", 
                                    "https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/ij/intellij-dependencies")
  );

  @NotNull
  public static List<RemoteRepositoryDescription> getRemoteRepositoryDescriptions() {
    String repoForTesting = System.getProperty("maven.repo.for.testing");
    if (repoForTesting != null) {
      return List.of(new RemoteRepositoryDescription(
        "intellij-dependencies",
        "IntelliJ Dependencies",
        repoForTesting));
    }
    return REPOS_FOR_TESTING;
  }
}
