// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.*;
import org.jetbrains.annotations.NotNull;


public final class JavaTestFixtureFactoryImpl extends JavaTestFixtureFactory {
  public JavaTestFixtureFactoryImpl() {
    IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(JavaModuleFixtureBuilder.class, MyJavaModuleFixtureBuilderImpl.class);
  }

  @Override
  public JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture) {
    return new JavaCodeInsightTestFixtureImpl(projectFixture, new TempDirTestFixtureImpl());
  }

  @Override
  public JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture) {
    return new JavaCodeInsightTestFixtureImpl(projectFixture, tempDirFixture);
  }

  @Override
  public TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@NotNull String name) {
    return new LightTestFixtureBuilderImpl<>(new LightIdeaTestFixtureImpl(ourJavaProjectDescriptor, name));
  }

  public static class MyJavaModuleFixtureBuilderImpl extends JavaModuleFixtureBuilderImpl<ModuleFixture> {
    public MyJavaModuleFixtureBuilderImpl(@NotNull TestFixtureBuilder<? extends IdeaProjectTestFixture> testFixtureBuilder) {
      super(testFixtureBuilder);
    }

    @NotNull
    @Override
    protected ModuleFixture instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }

  private static final LightProjectDescriptor ourJavaProjectDescriptor = LightJavaCodeInsightFixtureTestCase.JAVA_1_6;
}
