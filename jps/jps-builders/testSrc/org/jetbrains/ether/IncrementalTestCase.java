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
package org.jetbrains.ether;

import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.openapi.util.io.FileSystemUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.jps.builders.BuildResult;
import org.jetbrains.jps.builders.CompileScopeTestBuilder;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.builders.impl.logging.ProjectBuilderLoggerBase;
import org.jetbrains.jps.builders.logging.BuildLoggingManager;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.JpsModuleRootModificationUtil;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.java.JpsJavaLibraryType;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.model.serialization.PathMacroUtil;
import org.jetbrains.jps.util.JpsPathUtil;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author db
 * @since 26.07.11
 */
public abstract class IncrementalTestCase extends JpsBuildTestCase {

  private static final String MODULE_DIR_PREFIX = "module";
  private final String groupName;
  private File baseDir;
  private File workDir;
  private JpsSdk<JpsDummyElement> myJdk;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected IncrementalTestCase(final String name) {
    setName(name);
    groupName = name;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    baseDir = new File(PathManagerEx.getTestDataPath(getClass()) + File.separator + "compileServer" + File.separator + "incremental" + File.separator + groupName + File.separator + getProjectName());
    workDir = FileUtil.createTempDirectory("jps-build", null);

    FileUtil.copyDir(baseDir, workDir, file -> {
      String name = file.getName();
      return !name.endsWith(".new") && !name.endsWith(".delete");
    });

    String outputPath = getAbsolutePath("out");
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myProject).setOutputUrl(JpsPathUtil.pathToUrl(outputPath));
  }

  @Override
  protected File doGetProjectDir() {
    return workDir;
  }

  protected String getUrl(String pathRelativeToProjectRoot) {
    return JpsPathUtil.pathToUrl(getAbsolutePath(pathRelativeToProjectRoot));
  }

  @Override
  public String getAbsolutePath(final String pathRelativeToProjectRoot) {
    return FileUtil.toSystemIndependentName(workDir.getAbsolutePath()) + "/" + pathRelativeToProjectRoot;
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      super.tearDown();
    }
    finally {
      FileUtil.delete(workDir);
    }
  }

  protected void modify(int stage) {
    final String removedSuffix = stage == 0? ".remove" : ".remove" + stage;
    final String newSuffix = stage == 0? ".new" : ".new" + stage;

    FileUtil.processFilesRecursively(baseDir, file -> {
      if (file.getName().endsWith(removedSuffix)) {
        FileUtil.delete(getTargetFile(file, removedSuffix));
      }
      return true;
    });
    final long[] timestamp = {0};
    FileUtil.processFilesRecursively(baseDir, file -> {
      try {
        if (file.getName().endsWith(newSuffix)) {
          File targetFile = getTargetFile(file, newSuffix);
          FileUtil.copyContent(file, targetFile);
          timestamp[0] = Math.max(timestamp[0], FileSystemUtil.lastModified(targetFile));
        }
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return true;
    });
    sleepUntil(timestamp[0]);
  }

  private File getTargetFile(File sourceFile, final String suffix) {
    String path = FileUtil.getRelativePath(baseDir, sourceFile);
    assertNotNull(path);
    if (!path.contains(File.separator)) {
      path = "src" + File.separator + path;
    }
    return new File(workDir, StringUtil.trimEnd(path, suffix));
  }

  public BuildResult doTest() {
    setupInitialProject();

    return doTestBuild(1);
  }

  protected void setupInitialProject() {
    if (new File(workDir, PathMacroUtil.DIRECTORY_STORE_NAME).exists()) {
      getOrCreateJdk();
      loadProject(workDir.getAbsolutePath());
    }
    else {
      addModule();
    }
  }

  protected JpsModule addModule() {
    return addModule(StringUtil.capitalize(getProjectName()), "src");
  }

  protected JpsModule addModule(final String moduleName, final String srcRootRelativePath) {
    String srcPath = getAbsolutePath(srcRootRelativePath);
    return addModule(moduleName, new String[]{srcPath}, null, null, getOrCreateJdk());
  }

  /**
   * If the test changes project model between makes (e.g. module renamed), the descriptor should be re-created before each build session
   * in order to ensure the project is loaded correctly. If project model is unchanged, teh project descriptor can be reused from the previous build session
   * @return true if project descriptor from previous session can be reused, false otherwise
   */
  protected boolean useCachedProjectDescriptorOnEachMake() {
    return true;
  }

  protected BuildResult doTestBuild(int makesCount) {
    StringBuilder log = new StringBuilder();
    String rootPath = FileUtil.toSystemIndependentName(workDir.getAbsolutePath()) + "/";
    ProjectDescriptor pd = createProjectDescriptor(new BuildLoggingManager(new StringProjectBuilderLogger(rootPath, log)));
    BuildResult result = null;
    try {
      final boolean reuseProjectId = useCachedProjectDescriptorOnEachMake();

      doBuild(pd, CompileScopeTestBuilder.rebuild().allModules()).assertSuccessful();

      for (int idx = 0; idx < makesCount; idx++) {
        // this will save the current build data state before any changes were done to the project model
        if (!reuseProjectId) {
          pd.release();
        }
        modify(idx);
        if (!reuseProjectId) {
          pd = createProjectDescriptor(new BuildLoggingManager(new StringProjectBuilderLogger(rootPath, log)));
        }
        result = doBuild(pd, CompileScopeTestBuilder.make().allModules());
      }

      File logFile = new File(baseDir.getAbsolutePath() + ".log");
      if (!logFile.exists()) {
        logFile = new File(baseDir, "build.log");
      }
      assertSameLinesWithFile(logFile.getAbsolutePath(), log.toString());
    }
    finally {
      pd.release();
    }

    assertNotNull(result);
    if (result.isSuccessful()) {
      checkMappingsAreSameAfterRebuild(result);
    }
    return result;
  }

  protected JpsSdk<JpsDummyElement> getOrCreateJdk() {
    if (myJdk == null) {
      myJdk = addJdk("IDEA jdk");
    }
    return myJdk;
  }

  protected JpsLibrary addLibrary(final String jarPath) {
    JpsLibrary library = myProject.addLibrary("l", JpsJavaLibraryType.INSTANCE);
    library.addRoot(new File(getAbsolutePath(jarPath)), JpsOrderRootType.COMPILED);
    return library;
  }

  protected void addTestRoot(JpsModule module, final String testRootRelativePath) {
    module.addSourceRoot(getUrl(testRootRelativePath), JavaSourceRootType.TEST_SOURCE);
  }

  protected Map<String, JpsModule> setupModules() {
    final File projectDir = getOrCreateProjectDir();
    final File[] moduleDirs = projectDir.listFiles((dir, name) -> name.startsWith(MODULE_DIR_PREFIX));

    if (moduleDirs != null && moduleDirs.length > 0) {
      final Map<String, JpsModule> modules = new HashMap<>();
      final List<String> moduleNames = new ArrayList<>();
      for (File moduleDir : moduleDirs) {
        final String name = moduleDir.getName().substring(MODULE_DIR_PREFIX.length());
        final JpsModule m = addModule(name, moduleDir.getName() + "/src");
        modules.put(name, m);
        moduleNames.add(name);
      }
      Collections.sort(moduleNames, Collections.reverseOrder());
      // set dependencies in alphabet reverse order
      JpsModule from = null;
      for (String name : moduleNames) {
        final JpsModule mod = modules.get(name);
        if (from != null) {
          JpsModuleRootModificationUtil.addDependency(from, mod);
        }
        from = mod;
      }
      return modules;
    }
    return Collections.emptyMap();
  }

  private static class StringProjectBuilderLogger extends ProjectBuilderLoggerBase {
    private final String myRoot;
    private final StringBuilder myLog;

    private StringProjectBuilderLogger(String root, StringBuilder log) {
      myRoot = root;
      myLog = log;
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    @Override
    protected void logLine(String line) {
      myLog.append(StringUtil.trimStart(line, myRoot)).append('\n');
    }
  }
}
