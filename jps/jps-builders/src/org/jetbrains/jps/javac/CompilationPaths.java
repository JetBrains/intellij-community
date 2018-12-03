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
  private final Collection<File> myModulePath;
  private final Collection<File> mySourcePath;

  public CompilationPaths(Collection<File> platformClasspath, Collection<File> classpath, Collection<File> upgradeModulePath, Collection<File> modulePath, Collection<File> sourcePath) {
    myPlatformClasspath = constCollection(platformClasspath);
    myClasspath = constCollection(classpath);
    myUpgradeModulePath = constCollection(upgradeModulePath);
    myModulePath = constCollection(modulePath);
    mySourcePath = constCollection(sourcePath);
  }

  private static <T> Collection<T> constCollection(Collection<T> col) {
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
  public Collection<File> getModulePath() {
    return myModulePath;
  }

  @NotNull
  public Collection<File> getSourcePath() {
    return mySourcePath;
  }

  public interface Builder {
    CompilationPaths create();

    Builder setPlatformClasspath(Collection<File> path);
    Builder setClasspath(Collection<File> path);
    Builder setUpgradeModulePath(Collection<File> path);
    Builder setModulePath(Collection<File> path);
    Builder setSourcePath(Collection<File> path);
  }

  public static CompilationPaths create(@Nullable Collection<File> platformCp,
                                        @Nullable Collection<File> cp,
                                        @Nullable Collection<File> upgradeModCp,
                                        @Nullable Collection<File> modulePath,
                                        @Nullable Collection<File> sourcePath) {
    return new CompilationPaths(platformCp, cp, upgradeModCp, modulePath, sourcePath);
  }

  public static Builder builder() {
    return new Builder() {
      private Collection<File> mySourcePath;
      private Collection<File> myModulePath;
      private Collection<File> myUpgradeModulePath;
      private Collection<File> myClasspath;
      private Collection<File> myPlatformCp;

      @Override
      public CompilationPaths create() {
        return CompilationPaths.create(myPlatformCp, myClasspath, myUpgradeModulePath, myModulePath, mySourcePath);
      }

      @Override
      public Builder setPlatformClasspath(Collection<File> path) {
        myPlatformCp = path;
        return this;
      }

      @Override
      public Builder setClasspath(Collection<File> path) {
        myClasspath = path;
        return this;
      }

      @Override
      public Builder setUpgradeModulePath(Collection<File> path) {
        myUpgradeModulePath = path;
        return this;
      }

      @Override
      public Builder setModulePath(Collection<File> path) {
        myModulePath = path;
        return this;
      }

      @Override
      public Builder setSourcePath(Collection<File> path) {
        mySourcePath = path;
        return this;
      }
    };
  }
}
