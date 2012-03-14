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
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.io.TestFileSystemBuilder;
import com.intellij.util.text.UniqueNameGenerator;
import groovy.lang.Closure;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.*;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.artifacts.Artifact;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.java.JavaBuilderLoggerImpl;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.server.ClasspathBootstrap;
import org.jetbrains.jps.server.ProjectDescriptor;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.util.io.TestFileSystemItem.fs;

/**
 * @author nik
 */
public abstract class ArtifactBuilderTestCase extends UsefulTestCase {
  private ProjectDescriptor myDescriptor;
  private Project myProject;
  private File myProjectDir;
  private TestArtifactBuilderLogger myArtifactBuilderLogger;
  private Sdk myJdk;

  protected void setUp() throws Exception {
    super.setUp();
    myProject = new Project();
    myProject.setProjectName(getProjectName());
    final File serverRoot = FileUtil.createTempDirectory("compile-server", null);
    Paths.getInstance().setSystemRoot(serverRoot);
    myArtifactBuilderLogger = new TestArtifactBuilderLogger();
  }

  @Override
  protected void tearDown() throws Exception {
    for (Artifact artifact : myProject.getArtifacts().values()) {
      FileUtil.delete(new File(FileUtil.toSystemDependentName(artifact.getOutputPath())));
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
  
  protected Artifact addArtifact(LayoutElementTestUtil.LayoutElementCreator root) {
    final String name = UniqueNameGenerator.generateUniqueName("a", myProject.getArtifacts().keySet());
    return addArtifact(name, root);
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
  
  protected Artifact addArtifact(String name, LayoutElementTestUtil.LayoutElementCreator root) {
    assertFalse("Artifact " + name + " already exists", myProject.getArtifacts().containsKey(name));
    Artifact artifact = new Artifact();
    artifact.setName(name);
    artifact.setRootElement(root.buildElement());
    
    artifact.setOutputPath(FileUtil.toSystemIndependentName(new File(getOrCreateProjectDir(), "out/artifacts/" + name).getAbsolutePath()));
    myProject.getArtifacts().put(name, artifact);
    return artifact;
  }

  protected Module addModule(String moduleName, @Nullable String srcPath) {
    if (myJdk == null) {
      try {
        myJdk = myProject.createSdk("JavaSDK", "jdk", "1.6", System.getProperty("java.home"), null);
        final List<String> paths = new LinkedList<String>();
        paths.add(FileUtil.toSystemIndependentName(ClasspathBootstrap.getResourcePath(Object.class).getCanonicalPath()));
        myJdk.setClasspath(paths);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    final Module module = myProject.createModule(moduleName, Closure.IDENTITY);
    module.forceInit();
    module.setSdk(myJdk);
    module.addDependency(myJdk, PredefinedDependencyScopes.getCOMPILE(), false);
    if (srcPath != null) {
      module.getContentRoots().add(srcPath);
      module.getSourceRoots().add(srcPath);
      module.setOutputPath("out/production/" + moduleName);
    }
    return module;
  }

  protected Library addProjectLibrary(String name, String jarPath) {
    final Library library = myProject.createLibrary(name, Closure.IDENTITY);
    library.forceInit();
    library.getClasspath().add(jarPath);
    return library;
  }

  protected void buildAll() {
    Collection<Artifact> artifacts = myProject.getArtifacts().values();
    buildArtifacts(artifacts.toArray(new Artifact[artifacts.size()]));
  }

  protected void buildArtifacts(Artifact... artifact) {
    doBuild(false, false, artifact);
  }

  private ProjectDescriptor createProjectDescriptor() {
    try {
      final File dataStorageRoot = Paths.getDataStorageRoot(myProject);
      ProjectTimestamps timestamps = new ProjectTimestamps(dataStorageRoot);
      BuildDataManager dataManager = new BuildDataManager(dataStorageRoot, true);
      return new ProjectDescriptor(myProject, new FSState(true), timestamps, dataManager, new BuildLoggingManager(myArtifactBuilderLogger,
                                                                                                                  new JavaBuilderLoggerImpl()));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private String getProjectName() {
    return getTestName(true);
  }

  private void doBuild(boolean force, final boolean shouldFail, Artifact... artifacts) {
    if (myDescriptor == null) {
      myDescriptor = createProjectDescriptor();
      myDescriptor.incUsageCounter();
    }
    myArtifactBuilderLogger.clear();
    IncProjectBuilder builder = new IncProjectBuilder(myDescriptor, BuilderRegistry.getInstance(), Collections.<String, String>emptyMap(), CanceledStatus.NULL);
    final List<BuildMessage> errorMessages = new ArrayList<BuildMessage>();
    final List<BuildMessage> infoMessages = new ArrayList<BuildMessage>();
    builder.addMessageHandler(new MessageHandler() {
      @Override
      public void processMessage(BuildMessage msg) {
        if (msg.getKind() == BuildMessage.Kind.ERROR) {
          errorMessages.add(msg);
        }
        else {
          infoMessages.add(msg);
        }
      }
    });
    builder.build(new AllProjectScope(myDescriptor.project, new HashSet<Artifact>(Arrays.asList(artifacts)), force), !force, false);
    if (shouldFail) {
      assertFalse("Build not failed as expected", errorMessages.isEmpty());
    }
    else {
      assertTrue("Build failed. \nErrors:\n" + errorMessages + "\nInfo messages:\n" + infoMessages, errorMessages.isEmpty());
    }
  }

  protected static String getJUnitJarPath() {
    final File file = PathManager.findFileInLibDirectory("junit.jar");
    assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
    return FileUtil.toSystemIndependentName(file.getAbsolutePath());
  }

  protected static void assertEmptyOutput(Artifact a1) {
    assertOutput(a1, fs());
  }

  protected void assertBuildFailed(Artifact a) {
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

  protected static void assertOutput(Artifact a, TestFileSystemBuilder expected) {
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
