// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class CompilationPaths {
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

  @NotNull
  public Iterable<? extends File> getPlatformClasspath() {
    return myPlatformClasspath;
  }

  @NotNull
  public Iterable<? extends File> getClasspath() {
    return myClasspath;
  }

  @NotNull
  public Iterable<? extends File> getUpgradeModulePath() {
    return myUpgradeModulePath;
  }

  @NotNull
  public ModulePath getModulePath() {
    return myModulePath;
  }

  @NotNull
  public Iterable<? extends File> getSourcePath() {
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
