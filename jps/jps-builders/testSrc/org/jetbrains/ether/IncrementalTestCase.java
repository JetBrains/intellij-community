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

import com.intellij.openapi.diagnostic.Logger;
import junit.framework.TestCase;
import junitx.framework.FileAssert;
import org.apache.log4j.Level;
import org.apache.log4j.PropertyConfigurator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.Project;
import org.jetbrains.jps.api.CanceledStatus;
import org.jetbrains.jps.idea.IdeaProjectLoader;
import org.jetbrains.jps.incremental.AllProjectScope;
import org.jetbrains.jps.incremental.BuilderRegistry;
import org.jetbrains.jps.incremental.FSState;
import org.jetbrains.jps.incremental.IncProjectBuilder;
import org.jetbrains.jps.incremental.storage.BuildDataManager;
import org.jetbrains.jps.incremental.storage.ProjectTimestamps;
import org.jetbrains.jps.server.ProjectDescriptor;

import java.io.*;
import java.util.Properties;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 26.07.11
 * Time: 0:34
 * To change this template use File | Settings | File Templates.
 */
public abstract class IncrementalTestCase extends TestCase {
  private static class RootStripper {
    private String root;

    void setRoot(final String root) {
      this.root = root;
    }

    String strip(final String s){
      if (s.startsWith(root)) {
        return s.substring(root.length());
      }
      
      return s;
    }
  } 

  static {
    Logger.setFactory(new Logger.Factory() {
          @Override
          public Logger getLoggerInstance(String category) {
            final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(category);

            final boolean affectedLogger = category.equals("#org.jetbrains.jps.incremental.java.JavaBuilder") ||
                                           category.equals("#org.jetbrains.jps.incremental.IncProjectBuilder");

            return new Logger() {
              @Override
              public boolean isDebugEnabled() {
                return affectedLogger;
              }

              @Override
              public void debug(@NonNls String message) {
              }

              @Override
              public void debug(@Nullable Throwable t) {
              }

              @Override
              public void debug(@NonNls String message, @Nullable Throwable t) {
              }

              @Override
              public void error(@NonNls String message, @Nullable Throwable t, @NonNls String... details) {
              }

              @Override
              public void info(@NonNls String message) {
                if (affectedLogger) {
                  logger.info(stripper.strip(message));
                }
              }

              @Override
              public void info(@NonNls String message, @Nullable Throwable t) {
              }

              @Override
              public void warn(@NonNls String message, @Nullable Throwable t) {
              }

              @Override
              public void setLevel(Level level) {
              }
            };
          }
        });
  }

  private static RootStripper stripper = new RootStripper();
  
  private final String groupName;
  private final String tempDir = System.getProperty("java.io.tmpdir");

  private String baseDir;
  private String workDir;

  protected IncrementalTestCase(final String name) throws Exception {
    super(name);
    groupName = name;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    baseDir = "jps/testData" + File.separator + "incremental" + File.separator;

    for (int i = 0; ; i++) {
      final File tmp = new File(tempDir + File.separator + "__temp__" + i);
      if (tmp.mkdir()) {
        workDir = tmp.getPath() + File.separator;
        break;
      }
    }

    copy(new File(getBaseDir()), new File(getWorkDir()));
  }

  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
//        delete(new File(workDir));
  }

  private String getDir(final String prefix) {
    final String name = getName();

    assert (name.startsWith("test"));

    final String result = Character.toLowerCase(name.charAt("test".length())) + name.substring("test".length() + 1);

    return prefix + groupName + File.separator + result;
  }

  private String getBaseDir() {
    return getDir(baseDir);
  }

  private String getWorkDir() {
    return getDir(workDir);
  }

  private void delete(final File file) throws Exception {
    if (file.isDirectory()) {
      final File[] files = file.listFiles();

      if (files != null) {
        for (File f : files) {
          delete(f);
        }
      }
    }

    if (!file.delete()) throw new IOException("could not delete file or directory " + file.getPath());
  }

  private static void copy(final File input, final File output) throws Exception {
    if (input.isDirectory()) {
      if (output.mkdirs()) {
        final File[] files = input.listFiles();

        if (files != null) {
          for (File f : files) {
            copy(f, new File(output.getPath() + File.separator + f.getName()));
          }
        }
      }
      else {
        throw new IOException("unable to create directory " + output.getPath());
      }
    }
    else if (input.isFile()) {
      final FileReader in = new FileReader(input);
      final FileWriter out = new FileWriter(output);

      try {
        int c;
        while ((c = in.read()) != -1) out.write(c);
      }
      finally {
        in.close();
        out.close();
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
      final File output = new File(path + File.separator + basename);

      if (copy) {
        copy(input, output);
      }
      else {
        output.delete();
      }
    }
  }

  private void initLoggers() {
    final Properties properties = new Properties();

    properties.setProperty("log4j.rootCategory", "INFO, A1");
    properties.setProperty("log4j.appender.A1", "org.apache.log4j.FileAppender");
    properties.setProperty("log4j.appender.A1.file", getWorkDir() + ".log");
    properties.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
    properties.setProperty("log4j.appender.A1.layout.ConversionPattern", "%m%n");

    PropertyConfigurator.configure(properties);
  }

  public void doTest() throws Exception {
    stripper.setRoot(getWorkDir() + File.separator);
    
    initLoggers();

    final String projectPath = getWorkDir() + File.separator + ".idea";
    final Project project = new Project();

    IdeaProjectLoader.loadFromPath(project, projectPath, "");

    final ProjectDescriptor projectDescriptor =
      new ProjectDescriptor(projectPath, project, new FSState(true), new ProjectTimestamps(projectPath),
                            new BuildDataManager(projectPath, true));
    final IncProjectBuilder builder = new IncProjectBuilder(projectDescriptor, BuilderRegistry.getInstance(), CanceledStatus.NULL);

    builder.build(new AllProjectScope(project, true), false, true);

    Thread.sleep(1000);

    modify();

    builder.build(new AllProjectScope(project, false), true, false);

    projectDescriptor.release();

    FileAssert.assertEquals(new File(getBaseDir() + ".log"), new File(getWorkDir() + ".log"));
  }
}
