// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.testFramework.builders;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.ModuleFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public interface JavaModuleFixtureBuilder<T extends ModuleFixture> extends ModuleFixtureBuilder<T> {

  enum MockJdkLevel {
    jdk14,
    jdk15
  }

  @NotNull
  JavaModuleFixtureBuilder setLanguageLevel(@NotNull LanguageLevel languageLevel);

  @NotNull
  JavaModuleFixtureBuilder addLibrary(@NonNls String libraryName, @NonNls String @NotNull ... classPath);

  @NotNull
  JavaModuleFixtureBuilder addLibrary(@NonNls String libraryName, @NotNull Map<OrderRootType, String[]> roots);

  @NotNull
  JavaModuleFixtureBuilder addLibraryJars(@NonNls String libraryName, @NonNls @NotNull String basePath, @NonNls String @NotNull ... jarNames);

  @NotNull
  JavaModuleFixtureBuilder addJdk(@NonNls @NotNull String jdkPath);

  void setMockJdkLevel(@NotNull MockJdkLevel level);
}
