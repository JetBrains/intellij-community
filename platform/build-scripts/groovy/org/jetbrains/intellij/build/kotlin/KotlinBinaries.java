// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.kotlin;

import groovy.lang.Closure;
import groovy.lang.Lazy;
import org.jetbrains.intellij.build.BuildMessages;
import org.jetbrains.intellij.build.BuildOptions;
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot;
import org.jetbrains.intellij.build.impl.CompilationTasksImplKt;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Sets up Kotlin compiler (downloaded from Marketplace) which is required for JPS to compile the repository
 */
public final class KotlinBinaries {
  public KotlinBinaries(Path communityHome, BuildOptions options, BuildMessages messages) {
    this.options = options;
    this.messages = messages;
    this.communityHome = communityHome;
  }

  public boolean isCompilerRequired() {
    return !CompilationTasksImplKt.areCompiledClassesProvided(options);
  }

  public Path getKotlinCompilerHome() {
    return kotlinCompilerHome;
  }

  public void setKotlinCompilerHome(Path kotlinCompilerHome) {
    this.kotlinCompilerHome = kotlinCompilerHome;
  }

  private final Path communityHome;
  private final BuildMessages messages;
  private final BuildOptions options;
  @Lazy private Path kotlinCompilerHome = new Closure<Path>(this, this) {
    public Path doCall(Object it) {
      Path compilerHome =
        KotlinCompilerDependencyDownloader.downloadAndExtractKotlinCompiler(new BuildDependenciesCommunityRoot(communityHome));
      Path kotlinc = compilerHome.resolve("bin/kotlinc");
      if (!Files.exists(kotlinc)) {
        throw new IllegalStateException("Kotlin compiler home is missing under the path: " + compilerHome);
      }

      return compilerHome;
    }

    public Path doCall() {
      return doCall(null);
    }
  }.call(new Closure<Path>(this, this) {
    public Path doCall(Object it) {
      Path compilerHome =
        KotlinCompilerDependencyDownloader.downloadAndExtractKotlinCompiler(new BuildDependenciesCommunityRoot(communityHome));
      Path kotlinc = compilerHome.resolve("bin/kotlinc");
      if (!Files.exists(kotlinc)) {
        throw new IllegalStateException("Kotlin compiler home is missing under the path: " + compilerHome);
      }

      return compilerHome;
    }

    public Path doCall() {
      return doCall(null);
    }
  });
}
