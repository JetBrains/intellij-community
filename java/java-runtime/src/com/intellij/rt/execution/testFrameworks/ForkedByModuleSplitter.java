/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.rt.execution.testFrameworks;

import com.intellij.rt.execution.CommandLineWrapper;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public abstract class ForkedByModuleSplitter {
  protected final ForkedDebuggerHelper myForkedDebuggerHelper = new ForkedDebuggerHelper();
  protected final String myWorkingDirsPath;
  protected final String myForkMode;
  protected final PrintStream myOut;
  protected final PrintStream myErr;
  protected final List   myNewArgs;
  protected String myDynamicClasspath;
  protected List myVMParameters;

  public ForkedByModuleSplitter(String workingDirsPath, String forkMode, PrintStream out, PrintStream err, List newArgs) {
    myWorkingDirsPath = workingDirsPath;
    myForkMode = forkMode;
    myOut = out;
    myErr = err;
    myNewArgs = newArgs;
  }

  public int startSplitting(String[] args,
                            String configName,
                            String commandLinePath) throws Exception {
    args = myForkedDebuggerHelper.excludeDebugPortFromArgs(args);

    myVMParameters = new ArrayList();
    final BufferedReader bufferedReader = new BufferedReader(new FileReader(commandLinePath));
    myDynamicClasspath = bufferedReader.readLine();
    try {
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        myVMParameters.add(line);
      }
    }
    finally {
      bufferedReader.close();
    }

    long time = System.currentTimeMillis();
    int result = startSplitting(args, configName);
    myForkedDebuggerHelper.closeDebugSocket();
    sendTime(time);
    return result;
  }

  //read output from wrappers
  protected int startChildFork(List args, File workingDir, String classpath) throws IOException, InterruptedException {
    List vmParameters = new ArrayList(myVMParameters);

    myForkedDebuggerHelper.setupDebugger(vmParameters);
    //noinspection SSBasedInspection
    final File tempFile = File.createTempFile("fork", "test");
    tempFile.deleteOnExit();
    final String testOutputPath = tempFile.getAbsolutePath();

    final ProcessBuilder builder = new ProcessBuilder();
    builder.add(vmParameters);
    builder.add("-classpath");
    if (myDynamicClasspath.length() > 0) {
      try {
        final File classpathFile = File.createTempFile("classpath", null);
        classpathFile.deleteOnExit();
        final PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(classpathFile), "UTF-8"));
        try {
          int idx = 0;
          while (idx < classpath.length()) {
            final int endIdx = classpath.indexOf(File.pathSeparator, idx);
            if (endIdx < 0) {
              writer.println(classpath.substring(idx));
              break;
            }
            writer.println(classpath.substring(idx, endIdx));
            idx = endIdx + File.pathSeparator.length();
          }
        }
        finally {
          writer.close();
        }

        builder.add(myDynamicClasspath);
        builder.add(CommandLineWrapper.class.getName());
        builder.add(classpathFile.getAbsolutePath());
      }
      catch (Throwable e) {
        builder.add(classpath);
      }
    }
    else {
      builder.add(classpath);
    }

    builder.add(getStarterName());
    builder.add(testOutputPath);
    builder.add(args);
    builder.setWorkingDir(workingDir);

    final Process exec = builder.createProcess();
    final int result = exec.waitFor();
    ForkedVMWrapper.readWrapped(testOutputPath, myOut, myErr);
    return result;
  }

  //read file with classes grouped by module
  protected int splitPerModule() throws IOException {
    int result = 0;
    final BufferedReader perDirReader = new BufferedReader(new FileReader(myWorkingDirsPath));
    try {
      final String packageName = perDirReader.readLine();
      String workingDir;
      while ((workingDir = perDirReader.readLine()) != null) {
        final String moduleName = perDirReader.readLine();
        final String classpath = perDirReader.readLine();
        try {

          List classNames = new ArrayList();
          final int classNamesSize = Integer.parseInt(perDirReader.readLine());
          for (int i = 0; i < classNamesSize; i++) {
            String className = perDirReader.readLine();
            if (className == null) {
              System.err.println("Class name is expected. Working dir: " + workingDir);
              result = -1;
              break;
            }
            classNames.add(className);
          }

          final int childResult = startPerModuleFork(moduleName, classNames, packageName, workingDir, classpath, result);
          result = Math.min(childResult, result);
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    finally {
      perDirReader.close();
    }
    return result;
  }

  protected abstract int startSplitting(String[] args, String configName) throws Exception;

  protected abstract int startPerModuleFork(String moduleName,
                                            List classNames,
                                            String packageName,
                                            String workingDir,
                                            String classpath,
                                            int result) throws Exception;

  protected abstract String getStarterName();
  
  protected void sendTime(long time) {}
  protected void sendTree(Object rootDescription) {}
}
