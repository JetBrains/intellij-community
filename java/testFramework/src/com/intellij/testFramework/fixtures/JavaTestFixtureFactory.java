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
package com.intellij.testFramework.fixtures;

import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public abstract class JavaTestFixtureFactory {
  private static final JavaTestFixtureFactory ourInstance;

  static {
    try {
      final Class<?> aClass = Class.forName("com.intellij.testFramework.fixtures.impl.JavaTestFixtureFactoryImpl");
      ourInstance = (JavaTestFixtureFactory)aClass.newInstance();
    }
    catch (Exception e) {
      throw new RuntimeException("Can't instantiate factory", e);
    }
  }

  public static JavaTestFixtureFactory getFixtureFactory() {
    return ourInstance;
  }

  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder();

  public abstract JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture);

  public abstract JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture);

  /**
   *
   * @deprecated use {@link JavaTestFixtureFactory#createFixtureBuilder(String)}
   */
  //also implicitly initializes ourInstance and registers java module fixture builder
  public static TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder() {
    return IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder();
  }
  public static TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name) {
    return IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name);
  }
}
