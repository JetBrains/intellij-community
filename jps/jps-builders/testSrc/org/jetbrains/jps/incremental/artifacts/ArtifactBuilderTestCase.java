/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.incremental.artifacts;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.util.ArrayUtil;
import com.intellij.util.io.DirectoryContentSpec;
import com.intellij.util.io.TestFileSystemBuilder;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.jps.builders.BuildResult;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.artifact.DirectoryArtifactType;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.intellij.util.io.TestFileSystemItem.fs;
import static org.jetbrains.jps.builders.CompileScopeTestBuilder.make;

/**
 * @author nik
 */
public abstract class ArtifactBuilderTestCase extends JpsBuildTestCase {
  protected static void createFileInArtifactOutput(JpsArtifact a, final String relativePath) {
    createFileInOutputDir(a.getOutputPath(), relativePath);
  }

  protected static void createFileInModuleOutput(JpsModule m, final String relativePath) {
    File outputDirectory = JpsJavaExtensionService.getInstance().getOutputDirectory(m, false);
    assertNotNull(outputDirectory);
    createFileInOutputDir(outputDirectory.getAbsolutePath(), relativePath);
  }

  private static void createFileInOutputDir(final String outputPath, final String relativePath) {
    try {
      boolean created = new File(outputPath, relativePath).createNewFile();
      assertTrue(created);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void tearDown() throws Exception {
    for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(myProject)) {
      String outputPath = artifact.getOutputPath();
      if (outputPath != null) {
        FileUtil.delete(new File(FileUtil.toSystemDependentName(outputPath)));
      }
    }
    super.tearDown();
  }

  public JpsArtifact addArtifact(LayoutElementTestUtil.LayoutElementCreator root) {
    Set<String> usedNames = getArtifactNames();
    final String name = UniqueNameGenerator.generateUniqueName("a", usedNames);
    return addArtifact(name, root);
  }

  private Set<String> getArtifactNames() {
    Set<String> usedNames = new HashSet<>();
    for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(myProject)) {
      usedNames.add(artifact.getName());
    }
    return usedNames;
  }

  protected JpsArtifact addArtifact(String name, LayoutElementTestUtil.LayoutElementCreator root) {
    assertFalse("JpsArtifact " + name + " already exists", getArtifactNames().contains(name));
    JpsArtifact artifact = JpsArtifactService.getInstance().addArtifact(myProject, name, root.buildElement(), DirectoryArtifactType.INSTANCE,
                                                                        JpsElementFactory.getInstance().createDummyElement());
    artifact.setOutputPath(getAbsolutePath("out/artifacts/" + name));
    return artifact;
  }

  protected JpsLibrary addProjectLibrary(String name, String jarPath) {
    final JpsLibrary library = myProject.getLibraryCollection().addLibrary(name, JpsJavaLibraryType.INSTANCE);
    library.addRoot(JpsPathUtil.pathToUrl(jarPath), JpsOrderRootType.COMPILED);
    return library;
  }

  protected void buildAll() {
    Collection<JpsArtifact> artifacts = JpsArtifactService.getInstance().getArtifacts(myProject);
    buildArtifacts(artifacts.toArray(new JpsArtifact[artifacts.size()]));
  }

  protected void buildArtifacts(JpsArtifact... artifacts) {
    doBuild(CompileScopeTestBuilder.make().allModules().artifacts(artifacts)).assertSuccessful();
  }

  protected void rebuildAllModulesAndArtifacts() {
    doBuild(CompileScopeTestBuilder.rebuild().allModules().allArtifacts()).assertSuccessful();
  }

  protected BuildResult buildAllModulesAndArtifacts() {
    return doBuild(make().allModules().allArtifacts());
  }

  protected static String getJUnitJarPath() {
    final File file = new File(assertOneElement(IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit3")));
    assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
    return FileUtil.toSystemIndependentName(file.getAbsolutePath());
  }

  protected static void assertEmptyOutput(JpsArtifact a) {
    assertOutput(a, fs());
  }

  protected void assertBuildFailed(JpsArtifact a) {
    doBuild(CompileScopeTestBuilder.make().allModules().artifact(a)).assertFailed();
  }

  protected void assertCopied(String... filePaths) {
    assertDeletedAndCopied(ArrayUtil.EMPTY_STRING_ARRAY, filePaths);
  }

  protected void assertDeletedAndCopied(String deletedPath, String... copiedPaths) {
    assertDeletedAndCopied(new String[]{deletedPath}, copiedPaths);
  }

  protected void assertDeletedAndCopied(String[] deletedPaths, String... copiedPaths) {
    assertCompiled(IncArtifactBuilder.BUILDER_NAME, copiedPaths);
    super.assertDeleted(deletedPaths);
  }

  @Override
  protected void assertDeleted(String... paths) {
    assertDeletedAndCopied(paths);
  }

  protected static void assertOutput(JpsArtifact a, TestFileSystemBuilder expected) {
    assertOutput(a.getOutputPath(), expected);
  }

  protected static void assertOutput(JpsArtifact a, DirectoryContentSpec expected) {
    assertOutput(a.getOutputPath(), expected);
  }

  protected void buildAllAndAssertUpToDate() {
    buildAll();
    assertUpToDate();
  }

  protected void assertUpToDate() {
    assertDeletedAndCopied(ArrayUtil.EMPTY_STRING_ARRAY);
  }

}
