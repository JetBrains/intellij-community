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
package org.jetbrains.jps.builders;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.io.TestFileSystemBuilder;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.impl.BuildTargetIndexImpl;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.IncProjectBuilder;
import org.jetbrains.jps.incremental.RebuildRequestedException;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.BuildTargetsState;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.indices.impl.IgnoredFileIndexImpl;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.*;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.JpsTypedLibrary;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.library.sdk.JpsSdkReference;
import org.jetbrains.jps.model.library.sdk.JpsSdkType;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.module.JpsSdkReferencesTable;
import org.jetbrains.jps.model.serialization.JpsProjectLoader;
import org.jetbrains.jps.model.serialization.PathMacroUtil;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import static org.jetbrains.jps.builders.CompileScopeTestBuilder.make;

/**
 * @author nik
 */
public abstract class JpsBuildTestCase extends UsefulTestCase {
  private File myProjectDir;
  protected JpsProject myProject;
  protected JpsModel myModel;
  private JpsSdk<JpsDummyElement> myJdk;
  protected File myDataStorageRoot;
  private TestProjectBuilderLogger myLogger;

  protected Map<String, String> myBuildParams;

  protected static void rename(String path, String newName) {
    try {
      File file = new File(FileUtil.toSystemDependentName(path));
      assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
      final File tempFile = new File(file.getParentFile(), "__" + newName);
      FileUtil.rename(file, tempFile);
      File newFile = new File(file.getParentFile(), newName);
      FileUtil.copyContent(tempFile, newFile);
      FileUtil.delete(tempFile);
      change(newFile.getPath());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myModel = JpsElementFactory.getInstance().createModel();
    myProject = myModel.getProject();
    myDataStorageRoot = FileUtil.createTempDirectory("compile-server-" + getProjectName(), null);
    myLogger = new TestProjectBuilderLogger();
    myBuildParams = new HashMap<String, String>();
  }

  @Override
  protected void tearDown() throws Exception {
    myProjectDir = null;
    super.tearDown();
  }

  protected static void assertOutput(final String outputPath, TestFileSystemBuilder expected) {
    expected.build().assertDirectoryEqual(new File(FileUtil.toSystemDependentName(outputPath)));
  }

  protected static void assertOutput(JpsModule module, TestFileSystemBuilder expected) {
    String outputUrl = JpsJavaExtensionService.getInstance().getOutputUrl(module, false);
    assertNotNull(outputUrl);
    assertOutput(JpsPathUtil.urlToPath(outputUrl), expected);
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
      long oldTimestamp = FileSystemUtil.lastModified(file);
      long time = System.currentTimeMillis();
      setLastModified(file, time);
      if (FileSystemUtil.lastModified(file) <= oldTimestamp) {
        setLastModified(file, time + 1);
        long newTimeStamp = FileSystemUtil.lastModified(file);
        if (newTimeStamp <= oldTimestamp) {
          //Mac OS and some versions of Linux truncates timestamp to nearest second
          setLastModified(file, time + 1000);
          newTimeStamp = FileSystemUtil.lastModified(file);
          assertTrue("Failed to change timestamp for " + file.getAbsolutePath(), newTimeStamp > oldTimestamp);
        }
        sleepUntil(newTimeStamp);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected static void sleepUntil(long time) {
    //we need this to ensure that the file won't be treated as changed by user during compilation and therefore marked for recompilation
    long delta;
    while ((delta = time - System.currentTimeMillis()) > 0) {
      try {
        //noinspection BusyWait
        Thread.sleep(delta);
      }
      catch (InterruptedException ignored) {
      }
    }
  }

  private static void setLastModified(File file, long time) {
    boolean updated = file.setLastModified(time);
    assertTrue("Cannot modify timestamp for " + file.getAbsolutePath(), updated);
  }

  protected static void delete(String filePath) {
    File file = new File(FileUtil.toSystemDependentName(filePath));
    assertTrue("File " + file.getAbsolutePath() + " doesn't exist", file.exists());
    final boolean deleted = FileUtil.delete(file);
    assertTrue("Cannot delete file " + file.getAbsolutePath(), deleted);
  }

  protected JpsSdk<JpsDummyElement> addJdk(final String name) {
    try {
      return addJdk(name, FileUtil.toSystemIndependentName(ClasspathBootstrap.getResourceFile(Object.class).getCanonicalPath()));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected JpsSdk<JpsDummyElement> addJdk(final String name, final String path) {
    String homePath = System.getProperty("java.home");
    String versionString = System.getProperty("java.version");
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> jdk = myModel.getGlobal().addSdk(name, homePath, versionString, JpsJavaSdkType.INSTANCE);
    jdk.addRoot(JpsPathUtil.pathToUrl(path), JpsOrderRootType.COMPILED);
    return jdk.getProperties();
  }

  protected String getProjectName() {
    return StringUtil.decapitalize(StringUtil.trimStart(getName(), "test"));
  }

  protected ProjectDescriptor createProjectDescriptor(final BuildLoggingManager buildLoggingManager) {
    try {
      BuildTargetIndexImpl targetIndex = new BuildTargetIndexImpl(myModel);
      ModuleExcludeIndex index = new ModuleExcludeIndexImpl(myModel);
      IgnoredFileIndexImpl ignoredFileIndex = new IgnoredFileIndexImpl(myModel);
      BuildDataPaths dataPaths = new BuildDataPathsImpl(myDataStorageRoot);
      BuildRootIndexImpl buildRootIndex = new BuildRootIndexImpl(targetIndex, myModel, index, dataPaths, ignoredFileIndex);
      BuildTargetsState targetsState = new BuildTargetsState(dataPaths, myModel, buildRootIndex);
      ProjectTimestamps timestamps = new ProjectTimestamps(myDataStorageRoot, targetsState);
      BuildDataManager dataManager = new BuildDataManager(dataPaths, targetsState, true);
      return new ProjectDescriptor(myModel, new BuildFSState(true), timestamps, dataManager, buildLoggingManager, index, targetsState,
                                   targetIndex, buildRootIndex, ignoredFileIndex);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void loadProject(String projectPath) {
    loadProject(projectPath, Collections.<String, String>emptyMap());
  }

  protected void loadProject(String projectPath,
                             Map<String, String> pathVariables) {
    try {
      String testDataRootPath = getTestDataRootPath();
      String fullProjectPath = FileUtil.toSystemDependentName(testDataRootPath != null ? testDataRootPath + "/" + projectPath : projectPath);
      Map<String, String> allPathVariables = new HashMap<String, String>(pathVariables.size() + 1);
      allPathVariables.putAll(pathVariables);
      allPathVariables.put(PathMacroUtil.APPLICATION_HOME_DIR, PathManager.getHomePath());
      addPathVariables(allPathVariables);
      JpsProjectLoader.loadProject(myProject, allPathVariables, fullProjectPath);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void addPathVariables(Map<String, String> pathVariables) {
  }

  @Nullable
  protected String getTestDataRootPath() {
    return null;
  }

  protected <T extends JpsElement> JpsModule addModule(String moduleName,
                                                       String[] srcPaths,
                                                       @Nullable String outputPath,
                                                       @Nullable String testOutputPath,
                                                       JpsSdk<T> sdk) {
    JpsModule module = myProject.addModule(moduleName, JpsJavaModuleType.INSTANCE);
    final JpsSdkType<T> sdkType = sdk.getSdkType();
    final JpsSdkReferencesTable sdkTable = module.getSdkReferencesTable();
    sdkTable.setSdkReference(sdkType, sdk.createReference());

    if (sdkType instanceof JpsJavaSdkTypeWrapper) {
      final JpsSdkReference<T> wrapperRef = sdk.createReference();
      sdkTable.setSdkReference(JpsJavaSdkType.INSTANCE, JpsJavaExtensionService.
        getInstance().createWrappedJavaSdkReference((JpsJavaSdkTypeWrapper)sdkType, wrapperRef));
    }
    module.getDependenciesList().addSdkDependency(sdkType);
    if (srcPaths.length > 0 || outputPath != null) {
      for (String srcPath : srcPaths) {
        module.getContentRootsList().addUrl(JpsPathUtil.pathToUrl(srcPath));
        module.addSourceRoot(JpsPathUtil.pathToUrl(srcPath), JavaSourceRootType.SOURCE);
      }
      JpsJavaModuleExtension extension = JpsJavaExtensionService.getInstance().getOrCreateModuleExtension(module);
      if (outputPath != null) {
        extension.setOutputUrl(JpsPathUtil.pathToUrl(outputPath));
        if (!StringUtil.isEmpty(testOutputPath)) {
          extension.setTestOutputUrl(JpsPathUtil.pathToUrl(testOutputPath));
        }
        else {
          extension.setTestOutputUrl(extension.getOutputUrl());
        }
      }
      else {
        extension.setInheritOutput(true);
      }
    }
    return module;
  }

  protected void rebuildAll() {
    doBuild(CompileScopeTestBuilder.rebuild().all()).assertSuccessful();
  }

  protected BuildResult makeAll() {
    return doBuild(make().all());
  }

  protected BuildResult doBuild(CompileScopeTestBuilder scope) {
    ProjectDescriptor descriptor = createProjectDescriptor(new BuildLoggingManager(myLogger));
    try {
      myLogger.clear();
      return doBuild(descriptor, scope);
    }
    finally {
      descriptor.release();
    }
  }

  public void assertCompiled(String builderName, String... paths) {
    myLogger.assertCompiled(builderName, new File[]{myProjectDir, myDataStorageRoot}, paths);
  }

  protected void assertDeleted(String... paths) {
    myLogger.assertDeleted(new File[]{myProjectDir, myDataStorageRoot}, paths);
  }

  protected BuildResult doBuild(final ProjectDescriptor descriptor, CompileScopeTestBuilder scopeBuilder) {
    IncProjectBuilder builder = new IncProjectBuilder(descriptor, BuilderRegistry.getInstance(), myBuildParams, CanceledStatus.NULL, null, true);
    BuildResult result = new BuildResult();
    builder.addMessageHandler(result);
    try {
      builder.build(scopeBuilder.build(), false);
    }
    catch (RebuildRequestedException e) {
      throw new RuntimeException(e);
    }
    return result;
  }

  protected String createFile(String relativePath) {
    return createFile(relativePath, "");
  }

  public String createFile(String relativePath, final String text) {
    try {
      File file = new File(getOrCreateProjectDir(), relativePath);
      FileUtil.writeToFile(file, text);
      return FileUtil.toSystemIndependentName(file.getAbsolutePath());
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected String copyToProject(String relativeSourcePath, String relativeTargetPath) {
    File source = findFindUnderProjectHome(relativeSourcePath);
    String fullTargetPath = getAbsolutePath(relativeTargetPath);
    File target = new File(fullTargetPath);
    try {
      if (source.isDirectory()) {
        FileUtil.copyDir(source, target);
      }
      else {
        FileUtil.copy(source, target);
      }
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return fullTargetPath;
  }

  protected File findFindUnderProjectHome(String relativeSourcePath) {
    return PathManagerEx.findFileUnderProjectHome(relativeSourcePath, getClass());
  }

  public File getOrCreateProjectDir() {
    if (myProjectDir == null) {
      try {
        myProjectDir = doGetProjectDir();
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return myProjectDir;
  }

  protected File doGetProjectDir() throws IOException {
    return FileUtil.createTempDirectory("prj", null);
  }

  public String getAbsolutePath(final String pathRelativeToProjectRoot) {
    return FileUtil.toSystemIndependentName(new File(getOrCreateProjectDir(), pathRelativeToProjectRoot).getAbsolutePath());
  }

  public JpsModule addModule(String moduleName, String... srcPaths) {
    if (myJdk == null) {
      myJdk = addJdk("1.6");
    }
    return addModule(moduleName, srcPaths, getAbsolutePath("out/production/" + moduleName), null, myJdk);
  }
}
