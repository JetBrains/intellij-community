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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.AllProjectScope;
import org.jetbrains.jps.incremental.BuildLoggingManager;
import org.jetbrains.jps.incremental.CompileScope;
import org.jetbrains.jps.incremental.IncProjectBuilder;
import org.jetbrains.jps.incremental.java.JavaBuilderLoggerImpl;
import org.jetbrains.jps.model.artifact.DirectoryArtifactType;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.artifact.JpsArtifactService;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsSdkProperties;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.util.io.TestFileSystemItem.fs;

/**
 * @author nik
 */
public abstract class ArtifactBuilderTestCase extends JpsBuildTestCase {
  private ProjectDescriptor myDescriptor;
  private File myProjectDir;
  private TestArtifactBuilderLogger myArtifactBuilderLogger;
  private JpsTypedLibrary<JpsSdkProperties> myJdk;

  protected void setUp() throws Exception {
    super.setUp();
    myArtifactBuilderLogger = new TestArtifactBuilderLogger();
  }

  @Override
  protected void tearDown() throws Exception {
    for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(myJpsProject)) {
      String outputPath = artifact.getOutputPath();
      if (outputPath != null) {
        FileUtil.delete(new File(FileUtil.toSystemDependentName(outputPath)));
      }
    }
    myDescriptor.release();
    myProjectDir = null;
    super.tearDown();
  }

  protected String createFile(String relativePath) {
    return createFile(relativePath, "");
  }

  protected String createFile(String relativePath, final String text) {
    try {
      File file = new File(getOrCreateProjectDir(), relativePath);
      FileUtil.writeToFile(file, text);
      return FileUtil.toSystemIndependentName(file.getAbsolutePath());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  protected JpsArtifact addArtifact(LayoutElementTestUtil.LayoutElementCreator root) {
    Set<String> usedNames = getArtifactNames();
    final String name = UniqueNameGenerator.generateUniqueName("a", usedNames);
    return addArtifact(name, root);
  }

  private Set<String> getArtifactNames() {
    Set<String> usedNames = new HashSet<String>();
    for (JpsArtifact artifact : JpsArtifactService.getInstance().getArtifacts(myJpsProject)) {
      usedNames.add(artifact.getName());
    }
    return usedNames;
  }

  private File getOrCreateProjectDir() {
    if (myProjectDir == null) {
      try {
        myProjectDir = FileUtil.createTempDirectory("prj", null);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return myProjectDir;
  }
  
  protected JpsArtifact addArtifact(String name, LayoutElementTestUtil.LayoutElementCreator root) {
    assertFalse("JpsArtifact " + name + " already exists", getArtifactNames().contains(name));
    JpsArtifact artifact = JpsArtifactService.getInstance().addArtifact(myJpsProject, name, root.buildElement(), DirectoryArtifactType.INSTANCE);
    artifact.setOutputPath(getAbsolutePath("out/artifacts/" + name));
    return artifact;
  }

  private String getAbsolutePath(final String pathRelativeToProjectRoot) {
    return FileUtil.toSystemIndependentName(new File(getOrCreateProjectDir(), pathRelativeToProjectRoot).getAbsolutePath());
  }

  protected JpsModule addModule(String moduleName, String... srcPaths) {
    if (myJdk == null) {
      myJdk = initJdk("1.6");
    }
    final JpsModule module = myJpsProject.addModule(moduleName, JpsJavaModuleType.INSTANCE);
    module.getSdkReferencesTable().setSdkReference(JpsJavaSdkType.INSTANCE, myJdk.createReference());
    module.getDependenciesList().addSdkDependency(JpsJavaSdkType.INSTANCE);
    if (srcPaths.length > 0) {
      for (String srcPath : srcPaths) {
        module.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(srcPath));
        module.addSourceRoot(JpsPathUtil.pathToUrl(srcPath), JavaSourceRootType.SOURCE);
      }
      final String outputUrl = JpsPathUtil.pathToUrl(getAbsolutePath("out/production/" + moduleName));
      JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module).setOutputUrl(outputUrl);
    }
    return module;
  }

  protected JpsLibrary addProjectLibrary(String name, String jarPath) {
    final JpsLibrary library = myJpsProject.getLibraryCollection().addLibrary(name, JpsJavaLibraryType.INSTANCE);
    library.addRoot(JpsPathUtil.pathToUrl(jarPath), JpsOrderRootType.COMPILED);
    return library;
  }

  protected void buildAll() {
    Collection<JpsArtifact> artifacts = JpsArtifactService.getInstance().getArtifacts(myJpsProject);
    buildArtifacts(artifacts.toArray(new JpsArtifact[artifacts.size()]));
  }

  protected void buildArtifacts(JpsArtifact... artifact) {
    doBuild(false, false, artifact);
  }

  private void doBuild(boolean force, final boolean shouldFail, JpsArtifact... artifacts) {
    if (myDescriptor == null) {
      myDescriptor = createProjectDescriptor(new BuildLoggingManager(myArtifactBuilderLogger, new JavaBuilderLoggerImpl()));
      myDescriptor.incUsageCounter();
    }
    myArtifactBuilderLogger.clear();
    IncProjectBuilder builder = createBuilder(myDescriptor);
    final CompileScope scope = new AllProjectScope(myDescriptor.project, myDescriptor.jpsProject,
                                                   new HashSet<JpsArtifact>(Arrays.asList(artifacts)), force);
    doBuild(builder, scope, shouldFail, !force, false);
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
    doBuild(false, true, a);
  }

  protected static void change(String filePath) {
    change(filePath, null);
  }

  protected static void change(String filePath, final @Nullable String newContent) {
    try {
      File file = new File(FileUtil.toSystemDependentName(filePath));
      assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
      if (newContent != null) {
        FileUtil.writeToFile(file, newContent);
      }
      boolean updated = file.setLastModified(file.lastModified() + 1000);
      assertTrue("Cannot modify timestamp for " + file.getAbsolutePath(), updated);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void delete(String filePath) {
    File file = new File(FileUtil.toSystemDependentName(filePath));
    assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
    final boolean deleted = FileUtil.delete(file);
    assertTrue("Cannot delete file " + file.getAbsolutePath(), deleted);
  }

  protected void assertCopied(String... filePaths) {
    assertSameElements(myArtifactBuilderLogger.myCopiedFilePaths, filePaths);
    assertEmpty(myArtifactBuilderLogger.myDeletedFilePaths);
  }

  protected void assertDeletedAndCopied(String deletedPath, String... copiedPaths) {
    assertSameElements(myArtifactBuilderLogger.myDeletedFilePaths, deletedPath);
    assertSameElements(myArtifactBuilderLogger.myCopiedFilePaths, copiedPaths);
  }

  protected static void assertOutput(JpsArtifact a, TestFileSystemBuilder expected) {
    expected.build().assertDirectoryEqual(new File(FileUtil.toSystemDependentName(a.getOutputPath())));
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

  private String getProjectRelativePath(String path) {
    assertNotNull(myProjectDir);
    final String projectDir = FileUtil.toSystemIndependentName(myProjectDir.getAbsolutePath());
    return FileUtil.getRelativePath(projectDir, path, '/');
  }

  protected static void rename(String path, String newName) {
    try {
      File file = new File(FileUtil.toSystemDependentName(path));
      assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
      FileUtil.rename(file, new File(file.getParentFile(), newName));
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
