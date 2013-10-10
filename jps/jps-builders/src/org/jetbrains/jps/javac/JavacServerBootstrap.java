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
package org.jetbrains.jps.javac;

import com.intellij.execution.process.BaseOSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.jps.cmdline.ClasspathBootstrap;
import org.jetbrains.jps.service.SharedThreadPool;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/**
 * @author Eugene Zhuravlev
 *         Date: 1/24/12
 */
public class JavacServerBootstrap {

  public static BaseOSProcessHandler launchJavacServer(String sdkHomePath,
                                                       int heapSize,
                                                       int port,
                                                       File workingDir,
                                                       List<String> vmOptions,
                                                       boolean useEclipseCompiler) throws Exception {
    final List<String> cmdLine = new ArrayList<String>();
    appendParam(cmdLine, getVMExecutablePath(sdkHomePath));
    appendParam(cmdLine, "-XX:MaxPermSize=150m");
    //appendParam(cmdLine, "-XX:ReservedCodeCacheSize=64m");
    appendParam(cmdLine, "-Djava.awt.headless=true");
    final int xms = heapSize / 2;
    if (xms > 32) {
      appendParam(cmdLine, "-Xms" + xms + "m");
    }
    appendParam(cmdLine, "-Xmx" + heapSize + "m");

    // debugging
    //appendParam(cmdLine, "-XX:+HeapDumpOnOutOfMemoryError");
    //appendParam(cmdLine, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5009");

    // javac's VM should use the same default locale that IDEA uses in order for javac to print messages in 'correct' language
    final String encoding = System.getProperty("file.encoding");
    if (encoding != null) {
      appendParam(cmdLine, "-Dfile.encoding=" + encoding);
    }
    final String lang = System.getProperty("user.language");
    if (lang != null) {
      //noinspection HardCodedStringLiteral
      appendParam(cmdLine, "-Duser.language=" + lang);
    }
    final String country = System.getProperty("user.country");
    if (country != null) {
      //noinspection HardCodedStringLiteral
      appendParam(cmdLine, "-Duser.country=" + country);
    }
    //noinspection HardCodedStringLiteral
    final String region = System.getProperty("user.region");
    if (region != null) {
      //noinspection HardCodedStringLiteral
      appendParam(cmdLine, "-Duser.region=" + region);
    }

    if (useEclipseCompiler) {
      appendParam(cmdLine, "-D" + JavacServer.USE_ECLIPSE_COMPILER_PROPERTY);
    }

    // this will disable standard extensions to ensure javac is loaded from the right tools.jar
    appendParam(cmdLine, "-Djava.ext.dirs=");

    for (String option : vmOptions) {
      appendParam(cmdLine, option);
    }

    appendParam(cmdLine, "-classpath");

    final List<File> cp = ClasspathBootstrap.getJavacServerClasspath(sdkHomePath, useEclipseCompiler);
    final StringBuilder classpath = new StringBuilder();
    for (File file : cp) {
      if (classpath.length() > 0) {
        classpath.append(File.pathSeparator);
      }
      classpath.append(file.getPath());
    }
    appendParam(cmdLine, classpath.toString());

    appendParam(cmdLine, org.jetbrains.jps.javac.JavacServer.class.getName());
    appendParam(cmdLine, Integer.toString(port));

    workingDir.mkdirs();

    appendParam(cmdLine, FileUtil.toSystemIndependentName(workingDir.getPath()));

    final ProcessBuilder builder = new ProcessBuilder(cmdLine);
    builder.directory(workingDir);

    final Process process = builder.start();
    final BaseOSProcessHandler processHandler = new BaseOSProcessHandler(process, null, null) {
      @Override
      protected Future<?> executeOnPooledThread(Runnable task) {
        return SharedThreadPool.getInstance().executeOnPooledThread(task);
      }
    };
    configureProcessHandler(processHandler);

    return processHandler;
  }

  private static void configureProcessHandler(final BaseOSProcessHandler processHandler) throws Exception {
    processHandler.addProcessListener(new ProcessAdapter() {
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        final String text = event.getText();
        if (!StringUtil.isEmptyOrSpaces(text)) {
          if (outputType == ProcessOutputTypes.STDOUT) {
            System.out.print("JAVAC_SERVER: " + text);
          }
          else if (outputType == ProcessOutputTypes.STDERR){
            System.err.print("JAVAC_SERVER: " + text);
          }
        }
      }
    });
    final Semaphore semaphore  = new Semaphore();
    semaphore.down();
    final Ref<String> serverStartMessage = new Ref<String>(null);
    processHandler.addProcessListener(new ProcessAdapter() {
      public void processTerminated(ProcessEvent event) {
        try {
          processHandler.removeProcessListener(this);
        }
        finally {
          semaphore.up();
        }
      }

      public void onTextAvailable(ProcessEvent event, Key outputType) {
        if (outputType == ProcessOutputTypes.STDERR) {
          try {
            final String text = event.getText();
            if (text != null) {
              if (text.contains(JavacServer.SERVER_SUCCESS_START_MESSAGE) || text.contains(JavacServer.SERVER_ERROR_START_MESSAGE)) {
                processHandler.removeProcessListener(this);
                serverStartMessage.set(text);
              }
            }
          }
          finally {
            semaphore.up();
          }
        }
      }
    });
    processHandler.startNotify();
    semaphore.waitFor();

    final String startupMsg = serverStartMessage.get();
    if (startupMsg == null || !startupMsg.contains(JavacServer.SERVER_SUCCESS_START_MESSAGE)) {
      throw new Exception("Server startup failed: " + startupMsg);
    }
  }

  private static void appendParam(List<String> cmdLine, String param) {
    if (SystemInfo.isWindows) {
      if (param.contains("\"")) {
        param = StringUtil.replace(param, "\"", "\\\"");
      }
      else if (param.length() == 0) {
        param = "\"\"";
      }
    }
    cmdLine.add(param);
  }

  public static String getVMExecutablePath(String sdkHome) {
    return sdkHome + "/bin/java";
  }
}
