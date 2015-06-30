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
package com.intellij.rt.execution.junit;

import com.intellij.rt.execution.CommandLineWrapper;

import java.io.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class ForkedStarter {
  protected final ForkedDebuggerStarter myForkedDebuggerStarter = new ForkedDebuggerStarter();

  //setup output wrappers
  protected void startVM(String[] args) throws Exception {
    final String testOutputPath = args[0];
    final File file = new File(testOutputPath);
    if (!file.exists()) {
      if (!file.createNewFile()) return;
    }
    final FileOutputStream stream = new FileOutputStream(testOutputPath);
    //noinspection UseOfSystemOutOrSystemErr
    PrintStream oldOut = System.out;
    //noinspection UseOfSystemOutOrSystemErr
    PrintStream oldErr = System.err;
    try {
      final PrintStream out = new PrintStream(new ForkedVMWrapper(stream, false));
      final PrintStream err = new PrintStream(new ForkedVMWrapper(stream, true));
      System.setOut(out);
      System.setErr(err);
      configureFrameworkAndRun(args, out, err);
    }
    finally {
      System.setOut(oldOut);
      System.setErr(oldErr);
      stream.close();
    }
  }
  
  //read output from wrappers
  protected int runChild(PrintStream out,
                         PrintStream err,
                         List vmParameters,
                         List args,
                         File workingDir,
                         String classpath,
                         String dynamicClasspath) throws IOException, InterruptedException {
    vmParameters = new ArrayList(vmParameters);

    myForkedDebuggerStarter.setupDebugger(vmParameters);
    //noinspection SSBasedInspection
    final File tempFile = File.createTempFile("fork", "test");
    tempFile.deleteOnExit();
    final String testOutputPath = tempFile.getAbsolutePath();

    final ProcessBuilder builder = new ProcessBuilder();
    builder.add(vmParameters);
    builder.add("-classpath");
    if (dynamicClasspath.length() > 0) {
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

        builder.add(dynamicClasspath);
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
    ForkedVMWrapper.readWrapped(testOutputPath, (PrintStream)out, (PrintStream)err);
    return result;
  }

  protected int processChildren(List args,
                                Object out,
                                Object err,
                                List parameters,
                                List children,
                                int result,
                                boolean forkTillMethod,
                                File workingDir,
                                String classpath,
                                String dynamicClasspath) throws IOException, InterruptedException {
    for (int i = 0, argsLength = children.size(); i < argsLength; i++) {
      final Object child = children.get(i);
      final List childTests = getChildren(child);
      final int childResult;
      if (childTests.isEmpty() || !forkTillMethod) {
        childResult = runChild(wrapOutputStream((OutputStream)out), wrapOutputStream((OutputStream)err),
                               parameters, createChildArgs(args, child), workingDir, classpath, dynamicClasspath);
      }
      else {
        childResult =
          processChildren(args, out, err, parameters, childTests, result, forkTillMethod, workingDir, classpath, dynamicClasspath);
      }
      result = Math.min(childResult, result);
    }
    return result;
  }

  public int startForkedVM(String workingDirsPath,
                           String[] args,
                           String configName,
                           Object out,
                           Object err,
                           String forkMode,
                           String commandLinePath, List newArgs) throws Exception {
    args = myForkedDebuggerStarter.excludeDebugPortFromArgs(args);

    final List parameters = new ArrayList();
    final BufferedReader bufferedReader = new BufferedReader(new FileReader(commandLinePath));
    final String dynamicClasspath = bufferedReader.readLine();
    try {
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        parameters.add(line);
      }
    }
    finally {
      bufferedReader.close();
    }

    final Object rootDescription = createRootDescription(args, newArgs, configName, out, err);
    if (rootDescription == null) return -1;

    sendTree(rootDescription);

    long time = System.currentTimeMillis();

    int result = 0;
    if (workingDirsPath == null || new File(workingDirsPath).length() == 0) {
       final List children = getChildren(rootDescription);
       final boolean forkTillMethod = forkMode.equalsIgnoreCase("method");
       result = processChildren(newArgs, out, err, parameters, children, 0, forkTillMethod, null, System.getProperty("java.class.path"), dynamicClasspath);
    } else {
      final BufferedReader perDirReader = new BufferedReader(new FileReader(workingDirsPath));
      try {
        final String packageName = perDirReader.readLine();
        String workingDir;
        while ((workingDir = perDirReader.readLine()) != null) {
          final String classpath = perDirReader.readLine();
          try {

            List classNames = new ArrayList();
            final int classNamesSize = Integer.parseInt(perDirReader.readLine());
            for (int i = 0; i < classNamesSize; i++) {
              String className = perDirReader.readLine();
              if (className == null) {
                System.err.println("Class name is expected. Working dir: " + workingDir);
                return -1;
              }
              classNames.add(className);
            }

            final Object rootDescriptor = findByClassName((String)classNames.get(0), rootDescription);
            final int childResult;
            final File dir = new File(workingDir);
            if (forkMode.equals("none")) {
              final List childArgs = createChildArgsForClasses(newArgs, packageName, workingDir, classNames, rootDescriptor);
              childResult = runChild(wrapOutputStream((OutputStream)out),
                                     wrapOutputStream((OutputStream)err),
                                     parameters, childArgs, dir, classpath, dynamicClasspath);
            }
            else {
              final List children = new ArrayList(getChildren(rootDescription));
              for (Iterator iterator = children.iterator(); iterator.hasNext(); ) {
                if (!classNames.contains(getTestClassName(iterator.next()))) {
                  iterator.remove();
                }
              }
              final boolean forkTillMethod = forkMode.equalsIgnoreCase("method");
              childResult = processChildren(newArgs, out, err, parameters, children, result, forkTillMethod, dir, classpath, dynamicClasspath);
            }
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
    }

    myForkedDebuggerStarter.closeDebugSocket();
    sendTime(time);
    return result;
  }

  protected abstract List createChildArgsForClasses(List newArgs,
                                                    String packageName,
                                                    String workingDir,
                                                    List classNames,
                                                    Object rootDescriptor) throws IOException;

  protected abstract Object createRootDescription(String[] args, List newArgs, String configName, Object out, Object err) throws Exception;

  protected void sendTree(Object rootDescription) {}
  protected void sendTime(long time) {}

  protected abstract Object findByClassName(String className, Object rootDescription);

  protected abstract String getTestClassName(Object child);

  protected abstract List createChildArgs(List args, Object child);

  protected abstract List getChildren(Object child);

  protected PrintStream wrapOutputStream(OutputStream out) {
    return (PrintStream)out;
  }

  protected abstract String getStarterName();

  protected abstract void configureFrameworkAndRun(String[] args, PrintStream out, PrintStream err) throws Exception;
}
