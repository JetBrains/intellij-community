// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;
import com.intellij.util.execution.ParametersListUtil;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.ProcessGroovyMethods;
import org.jetbrains.intellij.build.BuildMessages;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesManualRunOnly;

import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class ToolboxLiteGen {
  public static Path downloadToolboxLiteGen(BuildDependenciesCommunityRoot communityRoot, final String liteGenVersion) {
    URI liteGenUri = new URI("https://repo.labs.intellij.net/toolbox/lite-gen/lite-gen-" + liteGenVersion + ".zip");
    Path zip = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, liteGenUri);
    Path path = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, zip, BuildDependenciesExtractOptions.STRIP_ROOT);
    return path;
  }

  public static void runToolboxLiteGen(BuildDependenciesCommunityRoot communityRoot,
                                       BuildMessages messages,
                                       String liteGenVersion,
                                       String... args) {
    if (!SystemInfo.isUnix) {
      throw new IllegalStateException("Currently, lite gen runs only on Unix");
    }


    Path liteGenPath = downloadToolboxLiteGen(communityRoot, liteGenVersion);
    messages.info("Toolbox LiteGen is at " + String.valueOf(liteGenPath));

    Path binPath = liteGenPath.resolve("bin/lite");
    if (!Files.isExecutable(binPath)) {
      throw new IllegalStateException("File at \'" + String.valueOf(binPath) + "\' is missing or not executable");
    }


    final List<String> command = new ArrayList<String>();
    command.add(binPath.toString());
    command.addAll(DefaultGroovyMethods.toList(args));

    messages.info("Running " + ParametersListUtil.join(command));

    ProcessBuilder processBuilder = new ProcessBuilder(command);
    processBuilder.directory(liteGenPath.toFile());
    processBuilder.environment().put("JAVA_HOME", SystemProperties.getJavaHome());
    Process process = processBuilder.start();
    ProcessGroovyMethods.consumeProcessOutputStream(process, (OutputStream)System.out);
    ProcessGroovyMethods.consumeProcessErrorStream(process, (OutputStream)System.err);
    int rc = process.waitFor();
    if (rc != 0) {
      throw new IllegalStateException("\'" + DefaultGroovyMethods.join(command, " ") + "\' exited with exit code " + String.valueOf(rc));
    }
  }

  public static void main(String[] args) {
    Path path = downloadToolboxLiteGen(BuildDependenciesManualRunOnly.getCommunityRootFromWorkingDirectory(), "1.2.1553");
    DefaultGroovyMethods.println(this, "litegen is at " + String.valueOf(path));
  }
}
