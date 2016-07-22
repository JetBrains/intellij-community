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

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;

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
                            String commandLinePath,
                            String repeatCount) throws Exception {
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

    int result = startSplitting(args, configName, repeatCount);
    myForkedDebuggerHelper.closeDebugSocket();
    return result;
  }

  //read output from wrappers
  protected int startChildFork(List args, File workingDir, String classpath, String repeatCount) throws IOException, InterruptedException {
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
        builder.add(createClasspathJarFile(new Manifest(), classpath).getAbsolutePath());
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
    if (repeatCount != null) {
      builder.add(repeatCount);
    }
    builder.setWorkingDir(workingDir);

    final Process exec = builder.createProcess();
    final int result = exec.waitFor();
    ForkedVMWrapper.readWrapped(testOutputPath, myOut, myErr);
    return result;
  }

  //read file with classes grouped by module
  protected int splitPerModule(String repeatCount) throws IOException {
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

          final int childResult = startPerModuleFork(moduleName, classNames, packageName, workingDir, classpath, repeatCount, result);
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

  protected abstract int startSplitting(String[] args, String configName, String repeatCount) throws Exception;

  protected abstract int startPerModuleFork(String moduleName,
                                            List classNames,
                                            String packageName,
                                            String workingDir,
                                            String classpath,
                                            String repeatCount, int result) throws Exception;

  protected abstract String getStarterName();

  public static File createClasspathJarFile(Manifest manifest, String classpath) throws IOException {
    final Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");

    String classpathForManifest = "";
    int idx = 0;
    int endIdx = 0;
    while (endIdx >= 0) {
      endIdx = classpath.indexOf(File.pathSeparator, idx);
      String path = endIdx < 0 ? classpath.substring(idx) : classpath.substring(idx, endIdx);
      if (classpathForManifest.length() > 0) {
        classpathForManifest += " ";
      }
      try {
        //noinspection Since15
        classpathForManifest += new File(path).toURI().toURL().toString();
      }
      catch (NoSuchMethodError e) {
        classpathForManifest += new File(path).toURL().toString();
      }
      idx = endIdx + File.pathSeparator.length();
    }
    attributes.put(Attributes.Name.CLASS_PATH, classpathForManifest);

    File jarFile = File.createTempFile("classpath", ".jar");
    ZipOutputStream jarPlugin = null;
    try {
      BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(jarFile));
      jarPlugin = new JarOutputStream(out, manifest);
    }
    finally {
      if (jarPlugin != null) jarPlugin.close();
    }
    jarFile.deleteOnExit();
    return jarFile;
  }
}
