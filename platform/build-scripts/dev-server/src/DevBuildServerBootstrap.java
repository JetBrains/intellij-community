// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.devServer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DevBuildServerBootstrap {
  private static final Pattern regex = Pattern.compile(".+\\$MAVEN_REPOSITORY\\$/(.+)!/.+");

  public static void main(String[] args) throws Exception {
    Path m2RepositoryPath = Path.of(System.getProperty("user.home"), ".m2/repository");

    List<String> jarUrls = new ArrayList<>();
    Path classDir = Path.of(System.getenv("CLASSES_DIR"));
    jarUrls.add(classDir.resolve("intellij.platform.devBuildServer").toString());
    jarUrls.add(classDir.resolve("intellij.platform.util.rt.java8").toString());
    jarUrls.add(classDir.resolve("intellij.platform.util.zip").toString());
    jarUrls.add(classDir.resolve("intellij.platform.util.immutableKeyValueStore").toString());
    jarUrls.add(classDir.resolve("intellij.platform.buildScripts").toString());
    jarUrls.add(classDir.resolve("intellij.platform.buildScripts.downloader").toString());
    jarUrls.add(classDir.resolve("intellij.idea.community.build").toString());
    jarUrls.add(classDir.resolve("intellij.idea.community.build.tasks").toString());

    Path projectDir = Path.of(System.getProperty("ideaProjectHome", ".")).toAbsolutePath();
    jarUrls.add(projectDir.resolve("community/lib/ant/lib/") + "/*");

    for (String libFilename : List.of(
      "jps_build_script_dependencies_bootstrap.xml",
      "kotlinx_serialization_core.xml",
      "kotlinx_serialization_json.xml",
      "jsch_agent_proxy.xml",
      "jsch_agent_proxy_sshj.xml",
      "commons_compress.xml"
    )) {
      collectLibUrls(projectDir.resolve(".idea/libraries/" + libFilename), m2RepositoryPath, jarUrls);
    }

    List<String> command = new ArrayList<>();
    command.add(ProcessHandle.current().info().command().orElseThrow());
    //noinspection SpellCheckingInspection
    command.add("-Dfile.encoding=UTF-8");

    String additionalModules = System.getenv("ADDITIONAL_MODULES");
    if (additionalModules != null) {
      //noinspection SpellCheckingInspection
      command.add("-Dadditional.modules=" + additionalModules);
    }
    //noinspection SpellCheckingInspection
    command.add("-Dintellij.build.pycharm.shared.indexes=false");

    String extraSystemProperties = System.getenv("EXTRA_SYSTEM_PROPERTIES");
    if (extraSystemProperties != null && !extraSystemProperties.isEmpty()) {
      command.addAll(List.of(extraSystemProperties.split(" ")));
    }
    command.add("-classpath");
    command.add(String.join(File.pathSeparator, jarUrls));
    command.add("org.jetbrains.intellij.build.devServer.DevIdeaBuildServer");

    //System.out.println(String.join(" ", command));
    new ProcessBuilder(command).inheritIO().start().waitFor();
  }

  private static void collectLibUrls(Path libXml, Path m2RepositoryPath, List<String> jarUrls) throws IOException {
    List<String> lines = Files.readAllLines(libXml);
    for (String line : lines) {
      Matcher m = regex.matcher(line);
      if (m.matches()) {
        Path jar = m2RepositoryPath.resolve(m.group(1));
        if (Files.exists(jar)) {
          jarUrls.add(jar.toString());
        }
      }
    }
  }
}
