// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.roots.libraries;

import com.intellij.configurationStore.StoreReloadManager;
import com.intellij.jarRepository.RepositoryLibraryType;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.project.ProjectKt;
import com.intellij.roots.ModuleRootManagerTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.utils.library.RepositoryLibraryProperties;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class RepositoryLibrarySerializationTest extends ModuleRootManagerTestCase {
  @NotNull
  @Override
  protected Path getProjectDirOrFile() {
    return getProjectDirOrFile(true);
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

  @NotNull
  private RepositoryLibraryProperties loadLibrary(String name) throws IOException {
    LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    String libraryPath = "jps/model-serialization/testData/repositoryLibraries/.idea/libraries/" + name + ".xml";
    File librarySource = PathManagerEx.findFileUnderCommunityHome(libraryPath);

    WriteAction.runAndWait(() -> {
      VirtualFile librariesVirtualFile = VfsUtil.createDirectoryIfMissing(myProject.getBaseDir(), ".idea/libraries");
      VfsUtil.copy(this, LocalFileSystem.getInstance().refreshAndFindFileByIoFile(librarySource), librariesVirtualFile);
    });
    StoreReloadManager.getInstance().flushChangedProjectFileAlarm();

    LibraryTable projectLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(myProject);
    LibraryEx library = (LibraryEx)projectLibraryTable.getLibraryByName(name);
    assertNotNull(library);
    assertSame(RepositoryLibraryType.REPOSITORY_LIBRARY_KIND, library.getKind());
    RepositoryLibraryProperties properties = (RepositoryLibraryProperties)library.getProperties();
    assertNotNull(properties);
    return properties;
  }

  @NotNull
  @Override
  protected Project doCreateProject(@NotNull Path projectFile) throws Exception {
    Project project = super.doCreateProject(projectFile);
    // Force loading libraries from disk
    ProjectKt.getStateStore(project).setOptimiseTestLoadSpeed(false);
    return project;
  }
}
