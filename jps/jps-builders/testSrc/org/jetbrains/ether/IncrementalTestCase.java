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
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.cmdline.ProjectDescriptor;
import org.jetbrains.jps.incremental.*;
import org.jetbrains.jps.incremental.artifacts.ArtifactBuilderLoggerImpl;
import org.jetbrains.jps.incremental.java.JavaBuilderLogger;
import org.jetbrains.jps.incremental.messages.BuildMessage;
import org.jetbrains.jps.model.artifact.JpsArtifact;

import java.io.*;
import java.util.Collections;

/**
 * @author db
 * @since 26.07.11
 */
public abstract class IncrementalTestCase extends JpsBuildTestCase {
  private final String groupName;
  private final String tempDir = FileUtil.toSystemDependentName(new File(System.getProperty("java.io.tmpdir")).getCanonicalPath());

  private String baseDir;
  private String workDir;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  protected IncrementalTestCase(final String name) throws Exception {
    setName(name);
    groupName = name;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    baseDir = PathManagerEx.getTestDataPath() + File.separator + "compileServer" + File.separator + "incremental" + File.separator;

    for (int i = 0; ; i++) {
      final File tmp = new File(tempDir + File.separator + "__temp__" + i);
      if (tmp.mkdir()) {
        workDir = tmp.getPath() + File.separator;
        break;
      }
    }

    FileUtil.copyDir(new File(getBaseDir()), new File(getWorkDir()));

    Utils.setSystemRoot(new File(workDir));
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      super.tearDown();
    }
    finally {
      delete(new File(workDir));
    }
  }

  private String getDir(final String prefix) {
    return prefix + groupName + File.separator + getProjectName();
  }

  private String getBaseDir() {
    return getDir(baseDir);
  }

  private String getWorkDir() {
    return getDir(workDir);
  }

  private static void delete(final File file) throws Exception {
    final File[] files = file.listFiles();
    if (files != null) {
      // is directory
      for (File f : files) {
        delete(f);
      }
    }
    if (!file.delete()) {
      throw new IOException("could not delete file or directory " + file.getPath());
    }
  }

  private static void copy(final File input, final File output) throws Exception {
    if (input.isDirectory()) {
      if (output.mkdirs()) {
        final File[] files = input.listFiles();

        if (files != null) {
          for (File f : files) {
            copy(f, new File(output.getPath(), f.getName()));
          }
        }
      }
      else {
        throw new IOException("unable to create directory " + output.getPath());
      }
    }
    else if (input.isFile()) {
      FileReader in = null;
      FileWriter out = null;

      try {
        in = new FileReader(input);
        out = new FileWriter(output);
        int c;
        while ((c = in.read()) != -1) out.write(c);
      }
      finally {
        try {
          if (in != null) {
            in.close();
          }
        }
        finally {
          if (out != null) {
            out.close();
          }
        }
      }
    }
  }

  private void modify() throws Exception {
    final File dir = new File(getBaseDir());
    final File[] files = dir.listFiles(new FileFilter() {
      public boolean accept(final File pathname) {
        final String name = pathname.getName();

        return name.endsWith(".java.new") || name.endsWith(".java.remove");
      }
    });

    for (File input : files) {
      final String name = input.getName();

      final boolean copy = name.endsWith(".java.new");
      final String postfix = name.substring(0, name.length() - (copy ? ".new" : ".remove").length());
      final int pathSep = postfix.indexOf("$");
      final String basename = pathSep == -1 ? postfix : postfix.substring(pathSep + 1);
      final String path =
        getWorkDir() + File.separator + (pathSep == -1 ? "src" : postfix.substring(0, pathSep).replace('-', File.separatorChar));
      final File output = new File(path, basename);

      if (copy) {
        copy(input, output);
      }
      else {
        output.delete();
      }
    }
  }

  public void doTest() throws Exception {
    final String projectPath = getWorkDir() + File.separator + ".idea";

    initJdk("IDEA jdk");

    loadProject(projectPath);

    final TestJavaBuilderLogger javaBuilderLogger = new TestJavaBuilderLogger(FileUtil.toSystemIndependentName(getWorkDir() + File.separator));
    final ProjectDescriptor projectDescriptor = createProjectDescriptor(new BuildLoggingManager(new ArtifactBuilderLoggerImpl(), javaBuilderLogger));
    try {
      final IncProjectBuilder builder = createBuilder(projectDescriptor);
      doBuild(builder, new AllProjectScope(myProject, myJpsProject, Collections.<JpsArtifact>emptySet(), true), false, false, true);

      modify();

      if (SystemInfo.isUnix) {
        Thread.sleep(1000L);
      }

      final IncProjectBuilder makeBuilder = createBuilder(projectDescriptor);

      class MH implements MessageHandler {
        boolean myErrors = false;

        @Override
        public void processMessage(final BuildMessage msg) {
          if (msg.getKind() == BuildMessage.Kind.ERROR)
            myErrors = true;
        }
      }

      final MH handler = new MH();

      makeBuilder.addMessageHandler(handler);

      makeBuilder.build(new AllProjectScope(myProject, myJpsProject, Collections.<JpsArtifact>emptySet(), false), true, false, false);

      final ByteArrayOutputStream makeDump = new ByteArrayOutputStream();

      if (!handler.myErrors) {
        projectDescriptor.dataManager.getMappings().toStream(new PrintStream(makeDump));
      }

      makeDump.close();

      final String expected = StringUtil.convertLineSeparators(FileUtil.loadFile(new File(getBaseDir() + ".log")));
      final String actual = javaBuilderLogger.myLog.toString();

      assertEquals(expected, actual);

      if (!handler.myErrors) {
        createBuilder(projectDescriptor).build(new AllProjectScope(myProject, myJpsProject, Collections.<JpsArtifact>emptySet(), true), false, true, false);

        final ByteArrayOutputStream rebuildDump = new ByteArrayOutputStream();

        projectDescriptor.dataManager.getMappings().toStream(new PrintStream(rebuildDump));

        rebuildDump.close();

        assertEquals(rebuildDump.toString(), makeDump.toString());
      }
    }
    finally {
      projectDescriptor.release();
    }
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
