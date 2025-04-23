// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.rt.execution.testFrameworks;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipOutputStream;

/** @noinspection CallToPrintStackTrace*/
public abstract class ForkedByModuleSplitter {
  protected final ForkedDebuggerHelper myForkedDebuggerHelper = new ForkedDebuggerHelper();
  protected final String myWorkingDirsPath;
  protected final String myForkMode;
  protected final List<String> myNewArgs;
  protected String myDynamicClasspath;
  protected List<String> myVMParameters;

  public ForkedByModuleSplitter(String workingDirsPath, String forkMode, List<String> newArgs) {
    myWorkingDirsPath = workingDirsPath;
    myForkMode = forkMode;
    myNewArgs = newArgs;
  }

  public int startSplitting(String[] args,
                            String configName,
                            String commandLinePath,
                            String repeatCount) throws Exception {
    args = myForkedDebuggerHelper.excludeDebugPortFromArgs(args);

    myVMParameters = new ArrayList<>();
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
  protected int startChildFork(final List<String> args,
                               File workingDir,
                               String classpath,
                               List<String> moduleOptions,
                               String repeatCount) throws IOException, InterruptedException {
    List<String> vmParameters = new ArrayList<>(myVMParameters);

    myForkedDebuggerHelper.setupDebugger(vmParameters);
    final ProcessBuilder builder = new ProcessBuilder();
    builder.add(vmParameters);

    //copy encoding from first VM, as encoding is added into command line explicitly and vm options do not contain it
    String encoding = System.getProperty("file.encoding");
    if (encoding != null) {
      builder.add("-Dfile.encoding=" + encoding);
    }

    builder.add("-classpath");
    if (!myDynamicClasspath.isEmpty()) {
      try {
        if ("ARGS_FILE".equals(myDynamicClasspath)) {
          File argFile = File.createTempFile("arg_file", null);
          argFile.deleteOnExit();
          try (FileOutputStream writer = new FileOutputStream(argFile)) {
            String quotedArg = quoteArg(classpath);
            writer.write(quotedArg.getBytes(Charset.defaultCharset()));
          }
          builder.add("@" + argFile.getAbsolutePath());
        }
        else {
          builder.add(createClasspathJarFile(new Manifest(), classpath).getAbsolutePath());
        }
      }
      catch (Throwable e) {
        builder.add(classpath);
      }
    }
    else {
      builder.add(classpath);
    }

    if (moduleOptions != null) {
      builder.add(moduleOptions);
    }

    builder.add(getStarterName());
    builder.add(args);
    if (repeatCount != null) {
      builder.add(repeatCount);
    }
    builder.setWorkingDir(workingDir);

    final Process exec = builder.createProcess();
    new Thread(createInputReader(exec.getErrorStream(), System.err), "Read forked error output").start();
    new Thread(createInputReader(exec.getInputStream(), System.out), "Read forked output").start();
    return exec.waitFor();
  }

  /**
   * WARNING: Due to compatibility reasons, this method has duplicate: {@link com.intellij.execution.CommandLineWrapperUtil#quoteArg(String)}
   * If you modify this method, consider also changing its copy.
   */
  private static String quoteArg(String arg) {
    String specialCharacters = " #'\"\n\r\t\f";
    boolean containsSpecialCharacter = false;

    for (int i = 0; i < arg.length(); i++ ) {
      char ch = arg.charAt(i);
      if (specialCharacters.indexOf(ch) >= 0) {
        containsSpecialCharacter = true;
        break;
      }
    }

    if (!containsSpecialCharacter) return arg;

    StringBuilder sb = new StringBuilder(arg.length() * 2);
    for (int i = 0; i < arg.length(); i++) {
      char c = arg.charAt(i);
      if (c == ' ' || c == '#' || c == '\'') sb.append('"').append(c).append('"');
      else if (c == '"') sb.append("\"\\\"\"");
      else if (c == '\n') sb.append("\"\\n\"");
      else if (c == '\r') sb.append("\"\\r\"");
      else if (c == '\t') sb.append("\"\\t\"");
      else if (c == '\f') sb.append("\"\\f\"");
      else sb.append(c);
    }
    return sb.toString();
  }

  private static Runnable createInputReader(final InputStream inputStream, final PrintStream outputStream) {
    return new Runnable() {
      @Override
      public void run() {
        try {
          try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            while (true) {
              String line = inputReader.readLine();
              if (line == null) break;
              outputStream.println(line);
            }
          }
        }
        catch (UnsupportedEncodingException ignored) { }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    };
  }

  //read file with classes grouped by module
  protected int splitPerModule(String repeatCount) throws IOException {
    int result = 0;
    try (BufferedReader perDirReader = new BufferedReader(new FileReader(myWorkingDirsPath))) {
      final String packageName = perDirReader.readLine();
      String workingDir;
      while ((workingDir = perDirReader.readLine()) != null) {
        final String moduleName = perDirReader.readLine();
        final String classpath = perDirReader.readLine();
        List<String> moduleOptions = new ArrayList<>();
        String modulePath = perDirReader.readLine();
        if (modulePath != null && !modulePath.isEmpty()) {
          moduleOptions.add("-p");
          moduleOptions.add(modulePath);
        }
        final int optionsSize = Integer.parseInt(perDirReader.readLine());
        for (int i = 0; i < optionsSize; i++) {
          moduleOptions.add(perDirReader.readLine());
        }
        try {

          List<String> classNames = new ArrayList<>();
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

          String filters = perDirReader.readLine();
          final int childResult =
            startPerModuleFork(moduleName, classNames, packageName, workingDir, classpath, moduleOptions, repeatCount, result,
                               filters != null ? filters : "");
          result = Math.min(childResult, result);
        }
        catch (Exception e) {
          e.printStackTrace();
        }
      }
    }
    return result;
  }

  protected abstract int startSplitting(String[] args, String configName, String repeatCount) throws Exception;

  protected abstract int startPerModuleFork(String moduleName,
                                            List<String> classNames,
                                            String packageName,
                                            String workingDir,
                                            String classpath,
                                            List<String> moduleOptions,
                                            String repeatCount,
                                            int result,
                                            String filters) throws Exception;

  protected abstract String getStarterName();

  public static File createClasspathJarFile(Manifest manifest, String classpath) throws IOException {
    final Attributes attributes = manifest.getMainAttributes();
    attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");

    StringBuilder classpathForManifest = new StringBuilder();
    int idx = 0;
    int endIdx = 0;
    while (endIdx >= 0) {
      endIdx = classpath.indexOf(File.pathSeparator, idx);
      String path = endIdx < 0 ? classpath.substring(idx) : classpath.substring(idx, endIdx);
      if (classpathForManifest.length() > 0) {
        classpathForManifest.append(" ");
      }
      try {
        classpathForManifest.append(new File(path).toURI().toURL().toString());
      }
      catch (NoSuchMethodError e) {
        classpathForManifest.append(new File(path).toURL().toString());
      }
      idx = endIdx + File.pathSeparator.length();
    }
    attributes.put(Attributes.Name.CLASS_PATH, classpathForManifest.toString());

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
