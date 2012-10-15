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

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.io.TestFileSystemBuilder;
import com.intellij.util.text.UniqueNameGenerator;
import org.jetbrains.jps.builders.BuildResult;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.builders.impl.logging.ProjectBuilderLoggerImpl;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.artifact.DirectoryArtifactType;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.util.io.TestFileSystemItem.fs;

/**
 * @author nik
 */
public abstract class ArtifactBuilderTestCase extends JpsBuildTestCase {
  private TestArtifactBuilderLogger myArtifactBuilderLogger;

  protected void setUp() throws Exception {
    super.setUp();
    myArtifactBuilderLogger = new TestArtifactBuilderLogger();
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

  protected JpsArtifact addArtifact(LayoutElementTestUtil.LayoutElementCreator root) {
    Set<String> usedNames = getArtifactNames();
    final String name = UniqueNameGenerator.generateUniqueName("a", usedNames);
    return addArtifact(name, root);
  }

  private Set<String> getArtifactNames() {
    Set<String> usedNames = new HashSet<String>();
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

  protected BuildResult doBuild(CompileScopeTestBuilder scope) {
    BuildResult result;
    ProjectDescriptor descriptor = createProjectDescriptor(new BuildLoggingManager(myArtifactBuilderLogger, new ProjectBuilderLoggerImpl()));
    try {
      myArtifactBuilderLogger.clear();
      result = doBuild(descriptor, scope);
    }
    finally {
      descriptor.release();
    }
    return result;
  }

  protected static String getJUnitJarPath() {
    final File file = PathManager.findFileInLibDirectory("junit.jar");
    assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
    return FileUtil.toSystemIndependentName(file.getAbsolutePath());
  }

  protected static void assertEmptyOutput(JpsArtifact a1) {
    assertOutput(a1, fs());
  }

  protected void assertBuildFailed(JpsArtifact a) {
    doBuild(CompileScopeTestBuilder.make().allModules().artifact(a)).assertFailed();
  }

  protected void assertCopied(String... filePaths) {
    assertSameElements(myArtifactBuilderLogger.myCopiedFilePaths, filePaths);
    assertEmpty(myArtifactBuilderLogger.myDeletedFilePaths);
  }

  protected void assertDeletedAndCopied(String deletedPath, String... copiedPaths) {
    assertDeletedAndCopied(new String[]{deletedPath}, copiedPaths);
  }

  protected void assertDeletedAndCopied(String[] deletedPaths, String... copiedPaths) {
    assertSameElements(myArtifactBuilderLogger.myDeletedFilePaths, deletedPaths);
    assertSameElements(myArtifactBuilderLogger.myCopiedFilePaths, copiedPaths);
  }

  protected static void assertOutput(JpsArtifact a, TestFileSystemBuilder expected) {
    assertOutput(a.getOutputPath(), expected);
  }

  protected void assertDeleted(String... filePaths) {
    assertSameElements(myArtifactBuilderLogger.myDeletedFilePaths, filePaths);
    assertEmpty(myArtifactBuilderLogger.myCopiedFilePaths);
  }

  protected void buildAllAndAssertUpToDate() {
    buildAll();
    assertUpToDate();
  }

  protected void assertUpToDate() {
    assertEmpty(myArtifactBuilderLogger.myDeletedFilePaths);
    assertEmpty(myArtifactBuilderLogger.myCopiedFilePaths);
  }

  protected static void rename(String path, String newName) {
    try {
      File file = new File(FileUtil.toSystemDependentName(path));
      assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
      final File tempFile = new File(file.getParentFile(), "__" + newName);
      FileUtil.rename(file, tempFile);
      FileUtil.copyContent(tempFile, new File(file.getParentFile(), newName));
      FileUtil.delete(tempFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private class TestArtifactBuilderLogger implements ArtifactBuilderLogger {
    private Set<String> myCopiedFilePaths = new LinkedHashSet<String>();
    private Set<String> myDeletedFilePaths = new LinkedHashSet<String>();

    @Override
    public void fileCopied(String sourceFilePath) {
      myCopiedFilePaths.add(getProjectRelativePath(sourceFilePath));
    }

    @Override
    public void fileDeleted(String targetFilePath) {
      myDeletedFilePaths.add(getProjectRelativePath(targetFilePath));
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    public void clear() {
      myCopiedFilePaths.clear();
      myDeletedFilePaths.clear();
    }
  }
}
