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
package org.jetbrains.jps.incremental;

import com.intellij.execution.CommandLineWrapperUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Manifest;

/**
 * @author Eugene Zhuravlev
 *         Date: 10/28/11
 */
public class ExternalProcessUtil {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.jps.incremental.ExternalProcessUtil");

  private static class CommandLineWrapperClassHolder {
    static final Class ourWrapperClass;
    static {
      Class<?> aClass = null;
      try {
        aClass = Class.forName("com.intellij.rt.execution.CommandLineWrapper");
      }
      catch (Throwable ignored) {
      }
      ourWrapperClass = aClass;
    }
  }

  private static final char QUOTE = '\uEFEF';
  // please keep in sync with GeneralCommandLine.prepareCommand()
  public static String prepareCommand(String parameter) {
    if (SystemInfo.isWindows) {
      if (parameter.contains("\"")) {
        parameter = StringUtil.replace(parameter, "\"", "\\\"");
      }
      else if (parameter.length() == 0) {
        parameter = "\"\"";
      }
    }

    if (parameter.length() >= 2 && parameter.charAt(0) == QUOTE && parameter.charAt(parameter.length() - 1) == QUOTE) {
      parameter = '"' + parameter.substring(1, parameter.length() - 1) + '"';
    }

    return parameter;
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
                                                  List<String> programParams, final boolean useCommandLineWrapper) {
    final List<String> cmdLine = new ArrayList<String>();

    cmdLine.add(javaExecutable);

    for (String param : vmParams) {
      cmdLine.add(param);
    }

    if (!bootClasspath.isEmpty()) {
      cmdLine.add("-bootclasspath");
      cmdLine.add(StringUtil.join(bootClasspath, File.pathSeparator));
    }

    if (!classpath.isEmpty()) {
      List<String> commandLineWrapperArgs = null;
      if (useCommandLineWrapper) {
        final Class wrapperClass = getCommandLineWrapperClass();
        if (wrapperClass != null) {
          try {
            final String classpathFile = CommandLineWrapperUtil.createClasspathJarFile(new Manifest(), classpath).getAbsolutePath();
            commandLineWrapperArgs = Arrays.asList(
              "-classpath",
              classpathFile
            );
          }
          catch (IOException ex) {
            LOG.info("Error starting " + mainClass + "; Classpath wrapper will not be used: ", ex);
          }
        }
        else {
          LOG.info("CommandLineWrapper class not found, classpath wrapper will not be used");
        }
      }

      // classpath
      if (commandLineWrapperArgs != null) {
        cmdLine.addAll(commandLineWrapperArgs);
      }
      else {
        cmdLine.add("-classpath");
        cmdLine.add(StringUtil.join(classpath, File.pathSeparator));
      }
    }

    // main class and params
    cmdLine.add(mainClass);

    for (String param : programParams) {
      cmdLine.add(param);
    }

    return cmdLine;
  }

  @Nullable
  private static Class getCommandLineWrapperClass() {
    return CommandLineWrapperClassHolder.ourWrapperClass;
  }

}
