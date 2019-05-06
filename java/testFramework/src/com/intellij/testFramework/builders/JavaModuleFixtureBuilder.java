/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.testFramework.builders;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.fixtures.ModuleFixture;
import org.jetbrains.annotations.NonNls;

import java.util.Map;

/**
 * @author mike
 */
public interface JavaModuleFixtureBuilder<T extends ModuleFixture> extends ModuleFixtureBuilder<T> {

  enum MockJdkLevel {
    jdk14,
    jdk15
  }

  JavaModuleFixtureBuilder setLanguageLevel(LanguageLevel languageLevel);

  JavaModuleFixtureBuilder addLibrary(@NonNls String libraryName, @NonNls String... classPath);

  JavaModuleFixtureBuilder addLibrary(@NonNls String libraryName, Map<OrderRootType, String[]> roots);

  JavaModuleFixtureBuilder addLibraryJars(@NonNls String libraryName, @NonNls String basePath, @NonNls String... jarNames);

  JavaModuleFixtureBuilder addJdk(@NonNls String jdkPath);

  void setMockJdkLevel(MockJdkLevel level);
}
