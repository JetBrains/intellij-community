// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import externalApp.ExternalApp;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Script generator utility class. It uses to generate a temporary scripts that
 * are removed after application ends.
 */
public class ScriptGenerator {
  /**
   * The script prefix
   */
  private final String myPrefix;
  /**
   * The scripts may class
   */
  private final Class myMainClass;
  /**
   * The class paths for the script
   */
  private final ArrayList<File> myPaths = new ArrayList<>();
  /**
   * The internal parameters for the script
   */
  private final ArrayList<String> myInternalParameters = new ArrayList<>();

  /**
   * A constructor
   *
   * @param prefix    the script prefix
   * @param mainClass the script main class
   */
  public ScriptGenerator(@NotNull @NonNls String prefix, @NotNull Class mainClass) {
    myPrefix = prefix;
    myMainClass = mainClass;
    addClasses(myMainClass);
    addClasses(ExternalApp.class);
    addClasses(XmlRpcClientLite.class, DecoderException.class);
  }

  /**
   * Add jar or directory that contains the class to the classpath
   *
   * @param classes classes which sources will be added
   */
  private void addClasses(final Class... classes) {
    for (Class<?> c : classes) {
      File classPath = new File(PathUtil.getJarPathForClass(c));
      if (!myPaths.contains(classPath)) {
        // the size of path is expected to be quite small, so no optimization is done here
        myPaths.add(classPath);
      }
    }
  }

  /**
   * Add internal parameters for the script
   *
   * @param parameters internal parameters
   * @return this script generator
   */
  public ScriptGenerator addInternal(@NonNls String... parameters) {
    ContainerUtil.addAll(myInternalParameters, parameters);
    return this;
  }

  @NotNull
  private static File generateBatch(@NotNull @NonNls String fileName, @NotNull @NonNls String commandLine) throws IOException {
    @NonNls StringBuilder sb = new StringBuilder();
    sb.append("@echo off").append("\n");
    sb.append(commandLine).append(" %*").append("\n");
    return createTempExecutable(fileName + ".bat", sb.toString());
  }

  @NotNull
  private static File generateShell(@NotNull @NonNls String fileName, @NotNull @NonNls String commandLine) throws IOException {
    @NonNls StringBuilder sb = new StringBuilder();
    sb.append("#!/bin/sh").append("\n");
    sb.append(commandLine).append(" \"$@\"").append("\n");
    return createTempExecutable(fileName + ".sh", sb.toString());
  }

  @NotNull
  private static File createTempExecutable(@NotNull @NonNls String fileName, @NotNull @NonNls String content) throws IOException {
    File file = new File(PathManager.getTempPath(), fileName);
    FileUtil.writeToFile(file, content);
    FileUtil.setExecutable(file);
    return file;
  }

  @NotNull
  public File generate(boolean useBatchFile, @Nullable CustomScriptCommandLineBuilder customBuilder) throws IOException {
    String commandLine = commandLine(customBuilder);
    return useBatchFile ? generateBatch(myPrefix, commandLine)
                        : generateShell(myPrefix, commandLine);
  }

  /**
   * @return a command line for the customCmdBuilder program
   */
  @NonNls
  public String commandLine(@Nullable CustomScriptCommandLineBuilder customCmdBuilder) {
    @NonNls StringBuilder cmd = new StringBuilder();

    if (customCmdBuilder != null) {
      customCmdBuilder.buildJavaCmd(cmd);
    }
    else {
      cmd.append('"');
      cmd.append(String.format("%s/bin/java", System.getProperty("java.home")));
      cmd.append('"');
    }

    cmd.append(" -cp ");
    cmd.append('"');
    String classpathSeparator = String.valueOf(File.pathSeparatorChar);
    cmd.append(StringUtil.join(myPaths, file -> file.getPath(), classpathSeparator));
    cmd.append('"');

    cmd.append(' ');
    cmd.append(myMainClass.getName());

    for (String p : myInternalParameters) {
      cmd.append(' ');
      cmd.append(p);
    }

    String line = cmd.toString();
    if (SystemInfo.isWindows) {
      line = line.replace('\\', '/');
    }
    return line;
  }

  public interface CustomScriptCommandLineBuilder {
    void buildJavaCmd(StringBuilder cmd);
  }
}
