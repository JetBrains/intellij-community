// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.maven.utils.library;

import com.intellij.jarRepository.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.testFramework.ServiceContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.library.JpsMavenRepositoryLibraryDescriptor;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class RepositoryUtilsTest extends LibraryTest {
  @Override
  protected void setUp(){
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

  @Test
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
    var result = getResult(RepositoryUtils.deleteAndReloadDependencies(myProject, library));
    assertSize(1, result);

    // verify jar became valid
    assertEquals(validJar, fileContent(jarPath));
  }

  @Test
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
    var result = getResult(RepositoryUtils.deleteAndReloadDependencies(myProject, library));
    assertSize(1, result);

    // verify file still exists
    assertTrue(Files.exists(anotherPath));
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
