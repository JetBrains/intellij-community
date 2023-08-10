// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures;

import com.intellij.testFramework.fixtures.impl.JavaTestFixtureFactoryImpl;
import org.jetbrains.annotations.NotNull;


public abstract class JavaTestFixtureFactory {
  private static final JavaTestFixtureFactory ourInstance = new JavaTestFixtureFactoryImpl();

  public static JavaTestFixtureFactory getFixtureFactory() {
    return ourInstance;
  }

  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@NotNull String name);

  public abstract JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture);

  public abstract JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture);

  public static TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder(@NotNull String name) {
    return IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(name);
  }
}
