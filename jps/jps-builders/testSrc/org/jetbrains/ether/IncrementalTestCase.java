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
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Processor;
import org.jetbrains.jps.JpsPathUtil;
import org.jetbrains.jps.builders.BuildResult;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.AllProjectScope;
import org.jetbrains.jps.incremental.BuildLoggingManager;
import org.jetbrains.jps.incremental.Utils;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLoggerImpl;
import org.jetbrains.jps.incremental.java.JavaBuilderLogger;
import org.jetbrains.jps.model.JpsDummyElement;
import org.jetbrains.jps.model.artifact.JpsArtifact;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.library.sdk.JpsSdk;
import org.jetbrains.jps.model.module.JpsModule;

import java.io.*;
import java.util.Collections;

/**
 * @author db
 * @since 26.07.11
 */
public abstract class IncrementalTestCase extends JpsBuildTestCase {
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

    baseDir = new File(PathManagerEx.getTestDataPath() + File.separator + "compileServer" + File.separator + "incremental" + File.separator + groupName + File.separator + getProjectName());
    workDir = FileUtil.createTempDirectory("jps-build", null);

    FileUtil.copyDir(baseDir, workDir);

    String outputPath = getAbsolutePath("out");
    JpsJavaExtensionService.getInstance().getOrCreateProjectExtension(myJpsProject).setOutputUrl(JpsPathUtil.pathToUrl(outputPath));
  }

  protected String getUrl(String pathRelativeToProjectRoot) {
    return JpsPathUtil.pathToUrl(getAbsolutePath(pathRelativeToProjectRoot));
  }

  protected String getAbsolutePath(final String pathRelativeToProjectRoot) {
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

  private void modify() throws Exception {
    final File[] files = baseDir.listFiles(new FileFilter() {
      public boolean accept(final File pathname) {
        final String name = pathname.getName();
        return name.endsWith(".java.new") || name.endsWith(".java.remove");
      }
    });
    FileUtil.processFilesRecursively(baseDir, new Processor<File>() {
      @Override
      public boolean process(File file) {
        try {
          if (file.getName().endsWith(".form.new")) {
            String relativePath = StringUtil.trimEnd(FileUtil.getRelativePath(baseDir, file), ".new");
            FileUtil.copyContent(file, new File(workDir, relativePath));
          }
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        return true;
      }
    });

    for (File input : files) {
      final String name = input.getName();

      final boolean copy = name.endsWith(".new");
      final String postfix = name.substring(0, name.length() - (copy ? ".new" : ".remove").length());
      final int pathSep = postfix.indexOf("$");
      final String baseName = pathSep == -1 ? postfix : postfix.substring(pathSep + 1);
      final File path = new File(workDir, (pathSep == -1 ? "src" : postfix.substring(0, pathSep).replace('-', File.separatorChar)));
      final File output = new File(path, baseName);

      if (copy) {
        FileUtil.copyContent(input, output);
      }
      else {
        FileUtil.delete(output);
      }
    }
  }

  public BuildResult doTest() throws Exception {
    if (new File(workDir, ".idea").exists()) {
      getOrCreateJdk();
      loadProject(workDir.getAbsolutePath());
    }
    else {
      addModule();
    }

    return doTestBuild();
  }

  protected JpsModule addModule() {
    String moduleName = StringUtil.capitalize(getProjectName());
    String srcPath = getAbsolutePath("src");
    return addModule(moduleName, new String[]{srcPath}, null, getOrCreateJdk());
  }

  protected BuildResult doTestBuild() throws Exception {
    final TestJavaBuilderLogger
      javaBuilderLogger = new TestJavaBuilderLogger(FileUtil.toSystemIndependentName(workDir.getAbsolutePath()) + "/");
    final ProjectDescriptor
      projectDescriptor = createProjectDescriptor(new BuildLoggingManager(new ArtifactBuilderLoggerImpl(), javaBuilderLogger));
    try {
      doBuild(projectDescriptor, new AllProjectScope(myProject, myJpsProject, Collections.<JpsArtifact>emptySet(), true), false,
              true, false).assertSuccessful();

      modify();
      if (Utils.TIMESTAMP_ACCURACY > 1) {
        Thread.sleep(Utils.TIMESTAMP_ACCURACY);
      }


      BuildResult result = doBuild(projectDescriptor, new AllProjectScope(myProject, myJpsProject, Collections.<JpsArtifact>emptySet(), false), true, false, false);

      final ByteArrayOutputStream makeDump = new ByteArrayOutputStream();

      if (result.isSuccessful()) {
        projectDescriptor.dataManager.getMappings().toStream(new PrintStream(makeDump));
      }

      makeDump.close();

      File logFile = new File(baseDir.getAbsolutePath() + ".log");
      if (!logFile.exists()) {
        logFile = new File(baseDir, "build.log");
      }
      final String expected = StringUtil.convertLineSeparators(FileUtil.loadFile(logFile));
      final String actual = javaBuilderLogger.myLog.toString();

      assertEquals(expected, actual);

      if (result.isSuccessful()) {
        doBuild(projectDescriptor, new AllProjectScope(myProject, myJpsProject, Collections.<JpsArtifact>emptySet(), true), false,
                true, false).assertSuccessful();

        final ByteArrayOutputStream rebuildDump = new ByteArrayOutputStream();

        projectDescriptor.dataManager.getMappings().toStream(new PrintStream(rebuildDump));

        rebuildDump.close();

        assertEquals(rebuildDump.toString(), makeDump.toString());
      }
      return result;
    }
    finally {
      projectDescriptor.release();
    }
  }

  private JpsSdk<JpsDummyElement> getOrCreateJdk() {
    if (myJdk == null) {
      myJdk = addJdk("IDEA jdk");
    }
    return myJdk;
  }

  private static class TestJavaBuilderLogger implements JavaBuilderLogger {
    private final String myRoot;
    private final StringBuilder myLog;

    public TestJavaBuilderLogger(String root) {
      myRoot = root;
      myLog = new StringBuilder();
    }

    @Override
    public void log(String line) {
      myLog.append(StringUtil.trimStart(line, myRoot)).append('\n');
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }
}
