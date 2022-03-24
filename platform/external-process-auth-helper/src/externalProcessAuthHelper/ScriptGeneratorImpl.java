// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.externalProcessAuthHelper;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import externalApp.ExternalApp;
import org.apache.commons.codec.DecoderException;
import org.apache.xmlrpc.XmlRpcClientLite;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ScriptGeneratorImpl implements ScriptGenerator {
  private final List<String> myInternalParameters = new ArrayList<>();

  /**
   * Add internal parameters for the script
   */
  @NotNull
  public ScriptGeneratorImpl addParameters(@NonNls String... parameters) {
    ContainerUtil.addAll(myInternalParameters, parameters);
    return this;
  }

  @Override
  public @NotNull String commandLine(@NotNull Class<? extends ExternalApp> mainClass, boolean useBatchFile) {
    Set<File> jarPaths = new LinkedHashSet<>();
    addClasses(jarPaths, mainClass);
    addClasses(jarPaths, ExternalApp.class);
    addClasses(jarPaths, XmlRpcClientLite.class);
    addClasses(jarPaths, DecoderException.class);

    @NonNls StringBuilder cmd = new StringBuilder();

    cmd.append('"');
    cmd.append(getJavaExecutablePath());
    cmd.append('"');

    cmd.append(" -cp ");
    cmd.append('"');
    String classpathSeparator = String.valueOf(File.pathSeparatorChar);
    cmd.append(StringUtil.join(jarPaths, file -> file.getPath(), classpathSeparator));
    cmd.append('"');

    cmd.append(' ');
    cmd.append(mainClass.getName());

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

  @NotNull
  protected String getJavaExecutablePath() {
    return String.format("%s/bin/java", System.getProperty("java.home"));
  }

  private static void addClasses(@NotNull Set<File> paths, @NotNull Class<?> clazz) {
    paths.add(ScriptGeneratorUtil.getJarFileFor(clazz));
  }
}
