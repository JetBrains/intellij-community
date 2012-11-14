package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
            File classpathFile = FileUtil.createTempFile("classpath", null);
            final PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(classpathFile)));
            try {
              for (String path : classpath) {
                writer.println(path);
              }
            }
            finally {
              writer.close();
            }
            commandLineWrapperArgs = Arrays.asList(
              "-classpath",
              ClasspathBootstrap.getResourcePath(wrapperClass),
              wrapperClass.getName(),
              classpathFile.getAbsolutePath()
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
