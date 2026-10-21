// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.common.ThreadLeakTracker;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.io.DirectoryContentSpec;
import com.intellij.util.io.DirectoryContentSpecKt;
import com.intellij.util.io.TestFileSystemBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.builders.impl.BuildDataPathsImpl;
import org.jetbrains.jps.builders.impl.BuildRootIndexImpl;
import org.jetbrains.jps.builders.impl.BuildTargetIndexImpl;
import org.jetbrains.jps.builders.impl.BuildTargetRegistryImpl;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.builders.storage.BuildDataPaths;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.FSOperations;
import org.jetbrains.jps.incremental.IncProjectBuilder;
import org.jetbrains.jps.incremental.RebuildRequestedException;
import org.jetbrains.jps.incremental.fs.BuildFSState;
import org.jetbrains.jps.incremental.relativizer.PathRelativizerService;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.BuildTargetStateManagerImpl;
import org.jetbrains.jps.incremental.storage.BuildTargetsState;
import org.jetbrains.jps.indices.ModuleExcludeIndex;
import org.jetbrains.jps.indices.impl.IgnoredFileIndexImpl;
import org.jetbrains.jps.indices.impl.ModuleExcludeIndexImpl;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.JpsModel;
import org.jetbrains.jps.model.JpsProject;
import org.jetbrains.jps.model.java.JavaModuleIndex;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaModuleExtension;
import org.jetbrains.jps.model.java.JpsJavaModuleType;
import org.jetbrains.jps.model.java.JpsJavaSdkType;
import org.jetbrains.jps.model.java.JpsJavaSdkTypeWrapper;
import org.jetbrains.jps.model.java.compiler.JavaCompilers;
import org.jetbrains.jps.model.java.compiler.JpsJavaCompilerConfiguration;
import org.jetbrains.jps.model.java.impl.JavaModuleIndexImpl;
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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.jetbrains.jps.builders.CompileScopeTestBuilder.make;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The JUnit 5 replacement of {@code org.jetbrains.jps.builders.JpsBuildTestCase}.
 * <p>
 * A subclass gets a new JPS model, a new data storage directory and a new build log for each test.
 * The test creates the project files in a temporary directory, and JUnit deletes the directory after the test.
 * Call the methods from the test thread. The JPS build needs no application and no EDT.
 */
public abstract class JpsBuildTestBase {
  private static Disposable ourFreezeDetectorDisposable;

  @TempDir
  private Path myTempDir;
  private String myTestName;
  private File myProjectDir;
  @NotNull protected JpsProject myProject;
  protected JpsModel myModel;
  private JpsSdk<JpsDummyElement> myJdk;
  protected File myDataStorageRoot;
  private TestProjectBuilderLogger myLogger;

  protected Map<String, String> myBuildParams;

  protected static void rename(String path, String newName) {
    try {
      File file = new File(FileUtil.toSystemDependentName(path));
      assertTrue(file.exists(), "File " + file.getAbsolutePath() + " doesn't exist");
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

  @BeforeAll
  static void allowFreezeDetectorThread() {
    // The thread of a stopped FreezeDetector ends after its 5 s sleep, so the thread leak check must not wait for it
    ourFreezeDetectorDisposable = Disposer.newDisposable();
    ThreadLeakTracker.longRunningThreadCreated(ourFreezeDetectorDisposable, "FreezeDetector Thread");
  }

  @AfterAll
  static void disallowFreezeDetectorThread() {
    Disposer.dispose(ourFreezeDetectorDisposable);
  }

  @BeforeEach
  void setUpJpsBuildTest(TestInfo testInfo) throws IOException {
    myTestName = testInfo.getTestMethod().orElseThrow().getName();
    assumeTrue(shouldRunTest());
    myModel = JpsElementFactory.getInstance().createModel();
    myProject = myModel.getProject();
    myDataStorageRoot = createTempDirectory("compile-server-" + getProjectName());
    myLogger = new TestProjectBuilderLogger();
    myBuildParams = new HashMap<>();
  }

  /**
   * Returns {@code false} to skip the current test. The check runs before the setup of the test.
   */
  protected boolean shouldRunTest() {
    return true;
  }

  /**
   * Returns the name of the current test method without the {@code test} prefix.
   */
  protected @NotNull String getTestName(boolean lowercaseFirstLetter) {
    return PlatformTestUtil.getTestName(myTestName, lowercaseFirstLetter);
  }

  /**
   * Creates a new directory in the temporary directory of the test. JUnit deletes it after the test.
   */
  protected @NotNull File createTempDirectory(@NotNull String prefix) throws IOException {
    return Files.createTempDirectory(myTempDir, prefix).toRealPath().toFile();
  }

  protected static void assertOutput(final String outputPath, TestFileSystemBuilder expected) {
    expected.build().assertDirectoryEqual(new File(FileUtil.toSystemDependentName(outputPath)));
  }

  protected static void assertOutput(final String outputPath, DirectoryContentSpec expected) {
    DirectoryContentSpecKt.assertMatches(new File(outputPath), expected);
  }

  protected static void assertOutput(JpsModule module, TestFileSystemBuilder expected) {
    String outputUrl = JpsJavaExtensionService.getInstance().getOutputUrl(module, false);
    assertNotNull(outputUrl);
    assertOutput(JpsPathUtil.urlToPath(outputUrl), expected);
  }

  protected static void assertOutput(JpsModule module, DirectoryContentSpec expected) {
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
      assertTrue(file.exists(), "File " + file.getAbsolutePath() + " doesn't exist");
      if (newContent != null) {
        FileUtil.writeToFile(file, newContent);
      }
      long oldTimestamp = FSOperations.lastModified(file);
      long time = System.currentTimeMillis();
      setLastModified(file, time);
      if (FSOperations.lastModified(file) <= oldTimestamp) {
        setLastModified(file, time + 1);
        long newTimeStamp = FSOperations.lastModified(file);
        if (newTimeStamp <= oldTimestamp) {
          //Mac OS and some versions of Linux truncates timestamp to nearest second
          setLastModified(file, time + 1000);
          newTimeStamp = FSOperations.lastModified(file);
          assertTrue(newTimeStamp > oldTimestamp, "Failed to change timestamp for " + file.getAbsolutePath());
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
      TimeoutUtil.sleep(delta);
    }
  }

  private static void setLastModified(File file, long time) {
    boolean updated = file.setLastModified(time);
    assertTrue(updated, "Cannot modify timestamp for " + file.getAbsolutePath());
  }

  protected static void delete(String filePath) {
    File file = new File(FileUtil.toSystemDependentName(filePath));
    assertTrue(file.exists(), "File " + file.getAbsolutePath() + " doesn't exist");
    final boolean deleted = FileUtil.delete(file);
    assertTrue(deleted, "Cannot delete file " + file.getAbsolutePath());
  }

  protected static void assertExists(File file) {
    assertTrue(file.exists(), "File should exist " + file);
  }

  protected static void assertDoesntExist(File file) {
    assertFalse(file.exists(), "File should not exist " + file);
  }

  protected JpsSdk<JpsDummyElement> addJdk(final String name) {
    try {
      String pathToRtJar = ClasspathBootstrap.getResourcePath(Object.class);
      String path = pathToRtJar == null ? null : FileUtil.toSystemIndependentName(new File(pathToRtJar).getCanonicalPath());
      return addJdk(name, path);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected JpsSdk<JpsDummyElement> addJdk(final String name, @Nullable String jdkClassesRoot) {
    String homePath = System.getProperty("java.home");
    String versionString = System.getProperty("java.version");
    JpsTypedLibrary<JpsSdk<JpsDummyElement>> jdk = myModel.getGlobal().addSdk(name, homePath, versionString, JpsJavaSdkType.INSTANCE);
    if (jdkClassesRoot != null) {
      jdk.addRoot(JpsPathUtil.pathToUrl(jdkClassesRoot), JpsOrderRootType.COMPILED);
    }
    return jdk.getProperties();
  }

  protected String getProjectName() {
    return StringUtil.decapitalize(StringUtil.trimStart(myTestName, "test"));
  }

  protected ProjectDescriptor createProjectDescriptor(final BuildLoggingManager buildLoggingManager) {
    try {
      BuildTargetRegistryImpl targetRegistry = new BuildTargetRegistryImpl(myModel);
      ModuleExcludeIndex index = new ModuleExcludeIndexImpl(myModel);
      IgnoredFileIndexImpl ignoredFileIndex = new IgnoredFileIndexImpl(myModel);
      BuildDataPaths dataPaths = new BuildDataPathsImpl(myDataStorageRoot.toPath());
      BuildRootIndexImpl buildRootIndex = new BuildRootIndexImpl(targetRegistry, myModel, index, dataPaths, ignoredFileIndex);
      BuildTargetIndexImpl targetIndex = new BuildTargetIndexImpl(targetRegistry, buildRootIndex);
      BuildTargetsState targetsState = new BuildTargetsState(new BuildTargetStateManagerImpl(dataPaths, myModel));
      PathRelativizerService relativizer = new PathRelativizerService(myModel.getProject());
      BuildDataManager dataManager = BuildDataManager.create(dataPaths, targetsState, relativizer);
      return new ProjectDescriptor(
        myModel, new BuildFSState(true), dataManager, buildLoggingManager, index, targetIndex, buildRootIndex, ignoredFileIndex
      );
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  protected void loadProject(String projectPath) {
    loadProject(projectPath, Collections.emptyMap());
  }

  protected void loadProject(String projectPath, Map<String, String> pathVariables) {
    try {
      String testDataRootPath = getTestDataRootPath();
      String fullProjectPath = FileUtil.toSystemDependentName(testDataRootPath != null ? testDataRootPath + "/" + projectPath : projectPath);
      Map<String, String> allPathVariables = new HashMap<>(pathVariables.size() + 1);
      allPathVariables.putAll(pathVariables);
      allPathVariables.put(PathMacroUtil.APPLICATION_HOME_DIR, PathManager.getHomePath());
      allPathVariables.putAll(getAdditionalPathVariables());
      JpsProjectLoader.loadProject(myProject, allPathVariables, Paths.get(fullProjectPath));
      final JpsJavaCompilerConfiguration config = JpsJavaExtensionService.getInstance().getCompilerConfiguration(myProject);
      config.getCompilerOptions(JavaCompilers.JAVAC_ID).PREFER_TARGET_JDK_COMPILER = false;
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  protected Map<String, String> getAdditionalPathVariables() {
    return Collections.emptyMap();
  }

  @Nullable
  protected String getTestDataRootPath() {
    return null;
  }

  protected <T extends JpsElement> JpsModule addModule(@NotNull String moduleName,
                                                       String @NotNull [] srcPaths,
                                                       @Nullable String outputPath,
                                                       @Nullable String testOutputPath,
                                                       @Nullable JpsSdk<T> sdk) {
    JpsModule module = myProject.addModule(moduleName, JpsJavaModuleType.INSTANCE);
    if (sdk != null) {
      setupModuleSdk(module, sdk);
    }
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

  protected  <T extends JpsElement> void setupModuleSdk(@NotNull JpsModule module, @NotNull JpsSdk<T> sdk) {
    final JpsSdkType<T> sdkType = sdk.getSdkType();
    final JpsSdkReferencesTable sdkTable = module.getSdkReferencesTable();
    sdkTable.setSdkReference(sdkType, sdk.createReference());

    if (sdkType instanceof JpsJavaSdkTypeWrapper) {
      final JpsSdkReference<T> wrapperRef = sdk.createReference();
      sdkTable.setSdkReference(JpsJavaSdkType.INSTANCE, JpsJavaExtensionService.
        getInstance().createWrappedJavaSdkReference((JpsJavaSdkTypeWrapper)sdkType, wrapperRef));
    }
    // ensure jdk entry is the first one in dependency list
    module.getDependenciesList().clear();
    module.getDependenciesList().addSdkDependency(sdkType);
    module.getDependenciesList().addModuleSourceDependency();
  }

  protected void rebuildAllModules() {
    doBuild(CompileScopeTestBuilder.rebuild().allModules()).assertSuccessful();
  }

  /**
   * Invoked forced rebuild for all targets in the project. May lead to unpredictable results if some plugins add targets your test doesn't expect.
   * @deprecated use {@link #rebuildAllModules()} instead or directly add required target types to the scope via {@link CompileScopeTestBuilder#targetTypes}
   */
  @Deprecated
  protected void rebuildAll() {
    doBuild(CompileScopeTestBuilder.rebuild().all()).assertSuccessful();
  }

  protected BuildResult buildAllModules() {
    return doBuild(make().allModules());
  }

  /**
   * Invoked incremental build for all targets in the project. May lead to unpredictable results if some plugins add targets your test doesn't expect.
   *
   * @deprecated use {@link #buildAllModules()} instead or directly add required target types to the scope via {@link CompileScopeTestBuilder#targetTypes}
   */
  @Deprecated
  protected BuildResult makeAll() {
    return doBuild(make().all());
  }

  protected BuildResult doBuild(CompileScopeTestBuilder scope) {
    ProjectDescriptor descriptor = createProjectDescriptor(new BuildLoggingManager(myLogger));
    try {
      myLogger.clearFilesData();
      return doBuild(descriptor, scope);
    }
    finally {
      descriptor.release();
    }
  }

  protected void clearBuildLog() {
    myLogger.clearLog();
  }

  /**
   * Checks that the last build compiled exactly the given paths with the given builder.
   */
  public void assertCompiled(String builderName, String... paths) {
    myLogger.assertCompiled(builderName, new File[]{myProjectDir, myDataStorageRoot}, paths);
  }

  /**
   * Compares the full build log of the test with the expected log file.
   */
  public void checkFullLog(File expectedLogFile) {
    PlatformTestUtil.assertSameLinesWithFile(expectedLogFile.getAbsolutePath(), myLogger.getFullLog(myProjectDir, myDataStorageRoot));
  }

  protected void assertDeleted(String... paths) {
    myLogger.assertDeleted(new File[]{myProjectDir, myDataStorageRoot}, paths);
  }

  protected BuildResult doBuild(final ProjectDescriptor descriptor, CompileScopeTestBuilder scopeBuilder) {
    IncProjectBuilder builder = new IncProjectBuilder(descriptor, BuilderRegistry.getInstance(), myBuildParams, CanceledStatus.NULL, true);
    BuildResult result = new BuildResult();
    builder.addMessageHandler(result);
    try {
      beforeBuildStarted(descriptor);
      builder.build(scopeBuilder.build(), false);
      result.storeMappingsDump(descriptor);
    }
    catch (RebuildRequestedException | IOException e) {
      throw new RuntimeException(e);
    }
    finally {
      // the following code models module index reload after each make session
      JavaModuleIndex moduleIndex = JpsJavaExtensionService.getInstance().getJavaModuleIndex(descriptor.getProject());
      if (moduleIndex instanceof JavaModuleIndexImpl) {
        ((JavaModuleIndexImpl)moduleIndex).dropCache();
      }
    }
    return result;
  }

  protected void beforeBuildStarted(@NotNull ProjectDescriptor descriptor) {
  }

  protected void deleteFile(String relativePath) {
    delete(new File(getOrCreateProjectDir(), relativePath).getAbsolutePath());
  }

  protected void changeFile(String relativePath) {
    changeFile(relativePath, null);
  }

  protected void changeFile(String relativePath, String newContent) {
    change(new File(getOrCreateProjectDir(), relativePath).getAbsolutePath(), newContent);
  }

  protected String createFile(String relativePath) {
    return createFile(relativePath, "");
  }

  protected String createDir(String relativePath) {
    File dir = new File(getOrCreateProjectDir(), relativePath);
    boolean created = dir.mkdirs();
    if (!created && !dir.isDirectory()) {
      fail("Cannot create " + dir.getAbsolutePath() + " directory");
    }
    return FileUtil.toSystemIndependentName(dir.getAbsolutePath());
  }

  /**
   * Writes a file with the given text under the project directory, and returns its system-independent path.
   */
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
      FileUtil.copyFileOrDir(source, target);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return fullTargetPath;
  }

  protected File findFindUnderProjectHome(String relativeSourcePath) {
    File file = new File(PathManagerEx.getHomePath(getClass()), relativeSourcePath);
    assertTrue(file.exists(), "Cannot find " + file.getAbsolutePath());
    return file;
  }

  /**
   * Returns the project directory of the test. The first call creates it.
   */
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
    return createTempDirectory("prj");
  }

  /**
   * Returns the system-independent absolute path of a path under the project directory.
   */
  public String getAbsolutePath(final String pathRelativeToProjectRoot) {
    return FileUtil.toSystemIndependentName(new File(getOrCreateProjectDir(), pathRelativeToProjectRoot).getAbsolutePath());
  }

  /**
   * Returns the URL of a path under the project directory.
   */
  public @NotNull String getUrl(@NotNull String pathRelativeToProjectRoot) {
    return JpsPathUtil.pathToUrl(getAbsolutePath(pathRelativeToProjectRoot));
  }

  /**
   * Adds a Java module with the given source roots, the default output directory and the default JDK.
   */
  public JpsModule addModule(String moduleName, String... srcPaths) {
    return addModule(moduleName, srcPaths, getAbsolutePath(getModuleOutputRelativePath(moduleName)), null, getJdk());
  }

  protected final JpsSdk<JpsDummyElement> getJdk() {
    if (myJdk == null) {
      myJdk = addJdk("1.6");
    }
    return myJdk;
  }

  @NotNull
  protected static File getModuleOutput(JpsModule module) {
    String outputUrl = JpsJavaExtensionService.getInstance().getOutputUrl(module, false);
    return JpsPathUtil.urlToFile(outputUrl);
  }

  @NotNull
  protected String getModuleOutputRelativePath(JpsModule module) {
    return getModuleOutputRelativePath(module.getName());
  }

  @NotNull
  protected String getModuleOutputRelativePath(String moduleName) {
    return "out/production/" + moduleName;
  }

  protected void checkMappingsAreSameAfterRebuild(BuildResult makeResult) {
    String makeDump = makeResult.getMappingsDump();
    BuildResult rebuildResult = doBuild(CompileScopeTestBuilder.rebuild().allModules());
    rebuildResult.assertSuccessful();
    String rebuildDump = rebuildResult.getMappingsDump();
    assertEquals(rebuildDump, makeDump);
  }
}
