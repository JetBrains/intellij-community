// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public final class CompilationPaths {
  private final Iterable<? extends File> myPlatformClasspath;
  private final Iterable<? extends File> myClasspath;
  private final Iterable<? extends File> myUpgradeModulePath;
  private final ModulePath myModulePath;
  private final Iterable<? extends File> mySourcePath;

  public CompilationPaths(Iterable<? extends File> platformClasspath,
                          Iterable<? extends File> classpath,
                          Iterable<? extends File> upgradeModulePath,
                          ModulePath modulePath,
                          Iterable<? extends File> sourcePath) {
    myPlatformClasspath = platformClasspath;
    myClasspath = classpath;
    myUpgradeModulePath = upgradeModulePath;
    myModulePath = modulePath;
    mySourcePath = sourcePath;
  }

  public @NotNull Iterable<? extends File> getPlatformClasspath() {
    return myPlatformClasspath;
  }

  public @NotNull Iterable<? extends File> getClasspath() {
    return myClasspath;
  }

  public @NotNull Iterable<? extends File> getUpgradeModulePath() {
    return myUpgradeModulePath;
  }

  public @NotNull ModulePath getModulePath() {
    return myModulePath;
  }

  public @NotNull Iterable<? extends File> getSourcePath() {
    return mySourcePath;
  }

  public static CompilationPaths create(@Nullable Iterable<? extends File> platformCp,
                                        @Nullable Iterable<? extends File> cp,
                                        @Nullable Iterable<? extends File> upgradeModCp,
                                        @NotNull ModulePath modulePath,
                                        @Nullable Iterable<? extends File> sourcePath) {
    return new CompilationPaths(platformCp, cp, upgradeModCp, modulePath, sourcePath);
  }

}
