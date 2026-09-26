// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.incremental;

import com.intellij.execution.CommandLineWrapperUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.Manifest;

/**
 * @author Eugene Zhuravlev
 */
public final class ExternalProcessUtil {
  private static final Logger LOG = Logger.getInstance(ExternalProcessUtil.class);

  private static final class CommandLineWrapperClassHolder {
    static final Class<?> ourWrapperClass;
    static {
      Class<?> aClass = null;
      try {
        aClass = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      }
      catch (Throwable ignored) { }
      ourWrapperClass = aClass;
    }
  }

  public static List<String> buildJavaCommandLine(String javaExecutable,
                                                  String mainClass,
                                                  List<String> bootClasspath,
                                                  List<String> classpath,
                                                  List<String> vmParams,
                                                  List<String> programParams) {
    return buildJavaCommandLine(javaExecutable, mainClass, bootClasspath, classpath, vmParams, programParams, true);
  }

  public static List<String> buildJavaCommandLine(String javaExecutable,
                                                  String mainClass,
                                                  List<String> bootClasspath,
                                                  List<String> classpath,
                                                  List<String> vmParams,
                                                  List<String> programParams,
                                                  boolean shortenClasspath) {
    return buildJavaCommandLine(javaExecutable, mainClass, bootClasspath, classpath, vmParams, programParams, shortenClasspath, true);
  }

  public static List<String> buildJavaCommandLine(String javaExecutable,
                                                  String mainClass,
                                                  List<String> bootClasspath,
                                                  List<String> classpath,
                                                  List<String> vmParams,
                                                  List<String> programParams,
                                                  boolean shortenClasspath,
                                                  boolean preferClasspathJar) {
    List<String> cmdLine = new ArrayList<>();

    cmdLine.add(javaExecutable);

    cmdLine.addAll(vmParams);

    if (!bootClasspath.isEmpty()) {
      cmdLine.add("-bootclasspath");
      cmdLine.add(StringUtil.join(bootClasspath, File.pathSeparator));
    }

    if (!classpath.isEmpty()) {
      List<String> shortenedCp = null;

      if (shortenClasspath) {
        try {
          Charset cs = Charset.defaultCharset();  // todo detect JNU charset from VM options?
          if (isModularRuntime(javaExecutable)) {
            List<String> args = Arrays.asList("-classpath", StringUtil.join(classpath, File.pathSeparator));
            File argFile = CommandLineWrapperUtil.createArgumentFile(args, cs);
            shortenedCp = Collections.singletonList('@' + argFile.getAbsolutePath());
          }
          else if (preferClasspathJar) {
            File classpathJar = CommandLineWrapperUtil.createClasspathJarFile(new Manifest(), classpath);
            shortenedCp = Arrays.asList("-classpath", classpathJar.getAbsolutePath());
          }
          else {
            Class<?> wrapperClass = CommandLineWrapperClassHolder.ourWrapperClass;
            if (wrapperClass != null) {
              File classpathFile = CommandLineWrapperUtil.createWrapperFile(classpath, cs);
              shortenedCp = Arrays.asList(
                "-classpath", ClasspathBootstrap.getResourcePath(wrapperClass), wrapperClass.getName(), classpathFile.getAbsolutePath());
            }
            else {
              LOG.info("CommandLineWrapper class not found; classpath shortening won't be used");
            }
          }
        }
        catch (IOException e) {
          LOG.warn("can't create temp file; classpath shortening won't be used", e);
        }
      }

      // classpath
      if (shortenedCp != null) {
        cmdLine.addAll(shortenedCp);
      }
      else {
        cmdLine.add("-classpath");
        cmdLine.add(StringUtil.join(classpath, File.pathSeparator));
      }
    }

    // main class and params
    cmdLine.add(mainClass);

    cmdLine.addAll(programParams);

    return cmdLine;
  }

  private static boolean isModularRuntime(String javaExec) {
    File jreHome = new File(javaExec).getParentFile().getParentFile();
    return jreHome != null && (new File(jreHome, "lib/jrt-fs.jar").isFile() || new File(jreHome, "modules/java.base").isDirectory());
  }
}