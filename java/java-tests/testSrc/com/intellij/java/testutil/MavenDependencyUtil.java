// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.testutil;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.jarRepository.RemoteRepositoryDescription;
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

public class MavenDependencyUtil {
  /**
   * Adds a Maven library to given model
   * @param model root model to add a Maven library to
   * @param mavenCoordinates maven coordinates like groupID:artifactID:version
   */
  public static void addFromMaven(@NotNull ModifiableRootModel model, String mavenCoordinates) {
    List<RemoteRepositoryDescription> remoteRepositoryDescriptions = getRemoteRepositoryDescriptions();
    RepositoryLibraryProperties libraryProperties = new RepositoryLibraryProperties(mavenCoordinates, true);
    Collection<OrderRoot> roots = JarRepositoryManager
      .loadDependenciesModal(model.getProject(), libraryProperties, false, false, null, remoteRepositoryDescriptions);
    LibraryTable.ModifiableModel tableModel = model.getModuleLibraryTable().getModifiableModel();
    Library.ModifiableModel libraryModel = tableModel.createLibrary(mavenCoordinates).getModifiableModel();
    for (OrderRoot root : roots) {
      libraryModel.addRoot(root.getFile(), root.getType());
    }
    libraryModel.commit();
    tableModel.commit();
  }

  @NotNull
  private static List<RemoteRepositoryDescription> getRemoteRepositoryDescriptions() {
    return ContainerUtil.map(IntelliJProjectConfiguration.getRemoteRepositoryDescriptions(), repository ->
      new RemoteRepositoryDescription(repository.getId(), repository.getName(), repository.getUrl()));
  }
}
