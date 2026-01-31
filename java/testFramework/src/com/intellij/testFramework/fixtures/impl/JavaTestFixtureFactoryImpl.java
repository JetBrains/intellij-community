// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.fixtures.impl;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaTestFixtureFactory;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.ModuleFixture;
import com.intellij.testFramework.fixtures.TempDirTestFixture;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import org.jetbrains.annotations.NotNull;


public final class JavaTestFixtureFactoryImpl extends JavaTestFixtureFactory {
  public JavaTestFixtureFactoryImpl() {
    IdeaTestFixtureFactory.getFixtureFactory().registerFixtureBuilder(JavaModuleFixtureBuilder.class, MyJavaModuleFixtureBuilderImpl.class);
  }

  @Override
  public @NotNull JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture) {
    return new JavaCodeInsightTestFixtureImpl(projectFixture, new TempDirTestFixtureImpl());
  }

  @Override
  public @NotNull JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture,
                                                                      TempDirTestFixture tempDirFixture) {
    return new JavaCodeInsightTestFixtureImpl(projectFixture, tempDirFixture);
  }

  @Override
  public @NotNull TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder(@NotNull String name) {
    return new LightTestFixtureBuilderImpl<>(new LightIdeaTestFixtureImpl(ourJavaProjectDescriptor, name));
  }

  public static class MyJavaModuleFixtureBuilderImpl extends JavaModuleFixtureBuilderImpl<ModuleFixture> {
    public MyJavaModuleFixtureBuilderImpl(@NotNull TestFixtureBuilder<? extends IdeaProjectTestFixture> testFixtureBuilder) {
      super(testFixtureBuilder);
    }

    @Override
    protected @NotNull ModuleFixture instantiateFixture() {
      return new ModuleFixtureImpl(this);
    }
  }

  private static final LightProjectDescriptor ourJavaProjectDescriptor = LightJavaCodeInsightFixtureTestCase.JAVA_1_6;
}
