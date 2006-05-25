/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.testFramework.fixtures;

/**
 * @author mike
 */
public abstract class IdeaTestFixtureFactory {
  private static final IdeaTestFixtureFactory ourInstance;

  static {
    try {
      final Class<?> aClass = Class.forName("com.intellij.testFramework.fixtures.impl.IdeaTestFixtureFactoryImpl");
      ourInstance = (IdeaTestFixtureFactory)aClass.newInstance();
    }
    catch (Exception e) {
      throw new RuntimeException("Can't instnatiate factory", e);
    }
  }

  public static IdeaTestFixtureFactory getFixtureFactory() {
    return ourInstance;
  }

  public abstract IdeaTestFixture createLightFixture();
}
