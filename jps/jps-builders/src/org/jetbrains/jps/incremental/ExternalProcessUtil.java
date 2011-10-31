package org.jetbrains.jps.incremental;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.server.ClasspathBootstrap;

import java.io.*;
import java.util.ArrayList;
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
      File classpathFile = null;
      final Class wrapperClass = getCommandLineWrapperClass();
      if (wrapperClass != null) {
        try {
          classpathFile = FileUtil.createTempFile("classpath", null);
          final PrintWriter writer = new PrintWriter(new BufferedWriter(new FileWriter(classpathFile)));
          try {
            for (String path : classpath) {
              writer.println(path);
            }
          }
          finally {
            writer.close();
          }
        }
        catch (IOException ex) {
          LOG.info("Error starting " + mainClass + "; Classpath wrapper will not be used: ", ex);
        }
      }

      // classpath
      if (classpathFile != null) {
        cmdLine.add("-classpath");
        final File wrapperClasspath = ClasspathBootstrap.getResourcePath(wrapperClass);
        cmdLine.add(wrapperClasspath.getPath());
        cmdLine.add(wrapperClass.getName());
        cmdLine.add(classpathFile.getAbsolutePath());
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
