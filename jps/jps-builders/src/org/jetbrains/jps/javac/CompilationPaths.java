// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Eugene Zhuravlev
 * Date: 14-Nov-18
 */
public class CompilationPaths {
  private final Collection<File> myPlatformClasspath;
  private final Collection<File> myClasspath;
  private final Collection<File> myUpgradeModulePath;
  private final ModulePath myModulePath;
  private final Collection<File> mySourcePath;

  public CompilationPaths(Collection<? extends File> platformClasspath,
                          Collection<? extends File> classpath,
                          Collection<? extends File> upgradeModulePath,
                          ModulePath modulePath,
                          Collection<? extends File> sourcePath) {
    myPlatformClasspath = constCollection(platformClasspath);
    myClasspath = constCollection(classpath);
    myUpgradeModulePath = constCollection(upgradeModulePath);
    myModulePath = modulePath;
    mySourcePath = constCollection(sourcePath);
  }

  private static <T> Collection<T> constCollection(Collection<? extends T> col) {
    return col == null || col.isEmpty()? Collections.emptyList() : Collections.unmodifiableCollection(col);
  }

  @NotNull
  public Collection<File> getPlatformClasspath() {
    return myPlatformClasspath;
  }

  @NotNull
  public Collection<File> getClasspath() {
    return myClasspath;
  }

  @NotNull
  public Collection<File> getUpgradeModulePath() {
    return myUpgradeModulePath;
  }

  @NotNull
  public ModulePath getModulePath() {
    return myModulePath;
  }

  @NotNull
  public Collection<File> getSourcePath() {
    return mySourcePath;
  }

  public static CompilationPaths create(@Nullable Collection<? extends File> platformCp,
                                        @Nullable Collection<? extends File> cp,
                                        @Nullable Collection<? extends File> upgradeModCp,
                                        @NotNull ModulePath modulePath,
                                        @Nullable Collection<? extends File> sourcePath) {
    return new CompilationPaths(platformCp, cp, upgradeModCp, modulePath, sourcePath);
  }

}
