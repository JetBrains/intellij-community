// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils.library;

import com.intellij.jarRepository.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.io.IoTestUtil;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class RepositoryUtilsTest extends LibraryTest {
  @Override
  protected void setUp() {
    super.setUp();

    // set up fake maven repository
    ServiceContainerUtil.registerServiceInstance(myProject, RemoteRepositoriesConfiguration.class, new RemoteRepositoriesConfiguration() {
      @NotNull
      @Override
      public List<RemoteRepositoryDescription> getRepositories() {
        return List.of(RepositoryUtilsTest.this.myMavenRepoDescription);
      }
    });
  }

  public void testLibraryReloadFixesCorruptedJar() throws IOException {
    var group = "test";
    var artifact = "test";
    var version = "0.0.1";
    var validJar = "ok";
    var corruptedJar = "not ok";

    var library = createLibrary();

    // create library in fake maven repository
    var repoFixture = new MavenRepoFixture(myMavenRepo);
    repoFixture.addLibraryArtifact(group, artifact, version, validJar);
    repoFixture.generateMavenMetadata(group, artifact);

    // create corrupted jar in fake local maven cache
    var cacheFixture = new MavenRepoFixture(myMavenLocalCache);
    var jarName = cacheFixture.addLibraryArtifact(group, artifact, version, corruptedJar);

    var jarPath = Paths.get(myMavenLocalCache.getPath(), group, artifact, version, jarName);
    var jarUrl = "jar://" + jarPath + "!/";

    var modifiableModel = library.getModifiableModel();
    modifiableModel.setKind(RepositoryLibraryType.REPOSITORY_LIBRARY_KIND);
    modifiableModel.addRoot(jarUrl, OrderRootType.CLASSES);
    modifiableModel.setProperties(new RepositoryLibraryProperties(new JpsMavenRepositoryLibraryDescriptor(group, artifact, version)));
    WriteCommandAction.runWriteCommandAction(myProject, () -> modifiableModel.commit());

    assertEquals(corruptedJar, fileContent(jarPath));

    // reload library
    getResult(RepositoryUtils.deleteAndReloadDependencies(myProject, library));

    // verify jar became valid
    assertTrue(jarPath + " should exist", Files.exists(jarPath));
    assertEquals(validJar, fileContent(jarPath));
  }

  public void testLibraryReloadDoesNotDeleteUnrelatedFiles() throws IOException {
    var group = "test";
    var artifact = "test";
    var version = "0.0.1";
    var validJar = "ok";

    var library = createLibrary();

    // create library in fake maven repository
    var repoFixture = new MavenRepoFixture(myMavenRepo);
    repoFixture.addLibraryArtifact(group, artifact, version, validJar);
    repoFixture.generateMavenMetadata(group, artifact);

    // create jar in fake local maven cache
    var cacheFixture = new MavenRepoFixture(myMavenLocalCache);
    var jarName = cacheFixture.addLibraryArtifact(group, artifact, version, validJar);

    var jarPath = Paths.get(myMavenLocalCache.getPath(), group, artifact, version, jarName);

    // create unrelated file in the same directory
    var anotherPath = Paths.get(myMavenLocalCache.getPath(), group, artifact, version, "unrelated" + jarName);
    Files.createFile(anotherPath);
    var jarUrl = "jar://" + jarPath + "!/";

    var modifiableModel = library.getModifiableModel();
    modifiableModel.setKind(RepositoryLibraryType.REPOSITORY_LIBRARY_KIND);
    modifiableModel.addRoot(jarUrl, OrderRootType.CLASSES);
    modifiableModel.setProperties(new RepositoryLibraryProperties(new JpsMavenRepositoryLibraryDescriptor(group, artifact, version)));
    WriteCommandAction.runWriteCommandAction(myProject, () -> modifiableModel.commit());

    // reload library
    getResult(RepositoryUtils.deleteAndReloadDependencies(myProject, library));

    // verify file still exists
    assertTrue(Files.exists(anotherPath));
  }

  public void testGetStorageRoot() {
    // zero urls returns null common root
    assertNull(RepositoryUtils.getStorageRoot(myProject, ArrayUtil.EMPTY_STRING_ARRAY));

    // one url, returns non-filename part
    assertEquals(FileUtil.toSystemDependentName("C:/path/to"),
                 RepositoryUtils.getStorageRoot(myProject, new String[]{"jar://C:/path/to/jar!/"}));
    assertEquals(FileUtil.toSystemDependentName("/Users/x/.m2.custom/repository/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2"),
                 RepositoryUtils.getStorageRoot(myProject, new String[]{
                   "jar:///Users/x/.m2.custom/repository/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2/jackson-jr-objects-2.17.2.jar!/"}));
    assertEquals(FileUtil.toSystemDependentName("/Users/x/.m2.custom/repository/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2"),
                 RepositoryUtils.getStorageRoot(myProject, new String[]{
                   "file:///Users/x/.m2.custom/repository/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2/jackson-jr-objects-2.17.2.jar"}));

    // two urls, different root
    assertNull(
      RepositoryUtils.getStorageRoot(
        myProject, new String[]{
          "file:///Users/x/.m2.custom/repository/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2/jackson-jr-objects-2.17.2.jar",
          "file:///Users/x/.m2.custom/repository/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.1/jackson-jr-objects-2.17.1.jar",
        }
      )
    );

    // two urls, same root
    assertEquals(
      FileUtil.toSystemDependentName("/Users/x/.m2.custom/repository/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2"),
      RepositoryUtils.getStorageRoot(
        myProject, new String[]{
          "file:///Users/x/.m2.custom/repository/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2/jackson-jr-objects-2.17.2.jar",
          "jar:///Users/x/.m2.custom/repository/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2/jackson-jr-objects-2.17.3.jar!/",
        }
      )
    );
  }

  public void testGetStorageRootWithSymlinks() throws IOException {
    IoTestUtil.assumeSymLinkCreationIsSupported();

    var temp = Files.createTempDirectory("storage-root").toRealPath();
    var dir = temp.resolve("dir");
    var dir2 = temp.resolve("dir2");
    var symlinkToDir = temp.resolve("symlink");

    Files.createDirectory(dir);
    Files.createSymbolicLink(symlinkToDir, dir);

    //var oldLocalRepositoryPath = JarRepositoryManager.getLocalRepositoryPath();
    //JarRepositoryManager.setLocalRepositoryPath(dir.toFile());
    var oldLocalRepositoryPath = JarRepositoryManager.getLocalRepositoryPath();

    var oldService = PathMacroManager.getInstance(myProject);
    var serviceDisposable = Disposer.newDisposable();
    ServiceContainerUtil.replaceService(myProject, PathMacroManager.class,
                                        new PathMacroManager(null) {
                                          @Override
                                          public String expandPath(@Nullable String text) {
                                            if ("$MAVEN_REPOSITORY$".equals(text)) {
                                              return dir.toAbsolutePath().toString();
                                            }
                                            return oldService.expandPath(text);
                                          }
                                        },
                                        serviceDisposable);
    try {
      // IJPL-175157 one url, should return null since it's under local repository, request symlinks resolve
      assertNull(
        RepositoryUtils.getStorageRoot(
          myProject, new String[]{
            JpsPathUtil.pathToUrl(symlinkToDir.toString()) +
            "/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2/jackson-jr-objects-2.17.2.jar",
          }
        )
      );

      // IJPL-175157 one url, should return null since it's under local repository
      assertNull(
        RepositoryUtils.getStorageRoot(
          myProject, new String[]{
            JpsPathUtil.pathToUrl(dir.toString()) + "/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2/jackson-jr-objects-2.17.2.jar",
          }
        )
      );

      // Another directory; should return it
      assertEquals(
        FileUtil.toSystemDependentName(dir2 + "/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2"),
        RepositoryUtils.getStorageRoot(
          myProject, new String[]{
            JpsPathUtil.pathToUrl(dir2.toString()) + "/com/fasterxml/jackson/jr/jackson-jr-objects/2.17.2/jackson-jr-objects-2.17.2.jar",
          }
        )
      );
    }
    finally {
      Disposer.dispose(serviceDisposable);
      //JarRepositoryManager.setLocalRepositoryPath(oldLocalRepositoryPath);
      FileUtil.deleteRecursively(temp);
    }
  }

  private static String fileContent(Path path) {
    try {
      return Files.readAllLines(path).get(0);
    }
    catch (IOException e) {
      return null;
    }
  }
}
