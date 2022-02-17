// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jpsBootstrap;

import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Objects;

import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.info;
import static org.jetbrains.jpsBootstrap.JpsBootstrapUtil.verbose;

public class KotlinCompiler {
  private static final String KOTLIN_IDE_MAVEN_REPOSITORY_URL = "https://cache-redirector.jetbrains.com/maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-ide-plugin-dependencies";

  public static Path downloadAndExtractKotlinCompiler(BuildDependenciesCommunityRoot communityRoot) throws Exception {
    // We already have kotlin JPS in the classpath, fetch version from it
    String kotlincVersion;
    try (InputStream inputStream = KotlinCompiler.class.getClassLoader().getResourceAsStream("META-INF/compiler.version")) {
      kotlincVersion = new String(Objects.requireNonNull(inputStream).readAllBytes(), StandardCharsets.UTF_8);
    }

    info("Kotlin compiler version is " + kotlincVersion);

    URI kotlincUrl = BuildDependenciesDownloader.getUriForMavenArtifact(
      KOTLIN_IDE_MAVEN_REPOSITORY_URL,
      "org.jetbrains.kotlin", "kotlin-dist-for-ide", kotlincVersion, "jar");
    Path kotlincDist = BuildDependenciesDownloader.downloadFileToCacheLocation(communityRoot, kotlincUrl);
    Path kotlinc = BuildDependenciesDownloader.extractFileToCacheLocation(communityRoot, kotlincDist);

    verbose("Kotlin compiler is at " + kotlinc);

    return kotlinc;
  }
}
