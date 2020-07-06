// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots.libraries;

import com.intellij.jarRepository.RepositoryLibraryType;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.components.impl.stores.IProjectStore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.project.ProjectKt;
import com.intellij.roots.ModuleRootManagerTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class RepositoryLibrarySerializationTest extends ModuleRootManagerTestCase {
  @Override
  protected boolean isCreateDirectoryBasedProject() {
    return true;
  }

  public void testPlain() throws IOException {
    RepositoryLibraryProperties properties = loadLibrary("plain");
    assertEquals("junit", properties.getGroupId());
    assertEquals("junit", properties.getArtifactId());
    assertEquals("3.8.1", properties.getVersion());
    assertTrue(properties.isIncludeTransitiveDependencies());
    assertEmpty(properties.getExcludedDependencies());
  }

  public void testWithoutTransitiveDependencies() throws IOException {
    RepositoryLibraryProperties properties = loadLibrary("without-transitive-dependencies");
    assertFalse(properties.isIncludeTransitiveDependencies());
    assertEmpty(properties.getExcludedDependencies());
  }

  public void testWithExcludedDependencies() throws IOException {
    RepositoryLibraryProperties properties = loadLibrary("with-excluded-dependencies");
    assertTrue(properties.isIncludeTransitiveDependencies());
    assertSameElements(properties.getExcludedDependencies(), "org.apache.httpcomponents:httpclient");
  }

  private @NotNull RepositoryLibraryProperties loadLibrary(@NotNull String name) throws IOException {
    Project project = myProject;
    LibraryTablesRegistrar.getInstance().getLibraryTable(project);
    String libraryPath = "jps/model-serialization/testData/repositoryLibraries/.idea/libraries/" + name + ".xml";
    Path librarySource = PathManagerEx.findFileUnderCommunityHome(libraryPath).toPath();

    IProjectStore stateStore = ProjectKt.getStateStore(project);
    Path dir = stateStore.getDirectoryStorePath().resolve("libraries");
    Files.createDirectories(dir);
    Path file = dir.resolve(librarySource.getFileName());
    Files.copy(librarySource, file);
    LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file);
    LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project);

    // force loading libraries from disk
    ProjectKt.getStateStore(project).setOptimiseTestLoadSpeed(false);
    try {
      stateStore.initComponent(projectLibraryTable, null, null);
    }
    finally {
      ProjectKt.getStateStore(project).setOptimiseTestLoadSpeed(true);
    }

    LibraryEx library = (LibraryEx)projectLibraryTable.getLibraryByName(name);
    assertThat(library).isNotNull();
    assertThat(library.getKind()).isSameAs(RepositoryLibraryType.REPOSITORY_LIBRARY_KIND);
    RepositoryLibraryProperties properties = (RepositoryLibraryProperties)library.getProperties();
    assertThat(properties).isNotNull();
    return properties;
  }
}
