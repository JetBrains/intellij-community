package com.intellij.testFramework.fixtures;

import com.intellij.testFramework.fixtures.impl.IdeaTestFixtureFactoryImpl;

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
      throw new RuntimeException("Can't instnatiate factory", e);
    }
  }

  public static JavaTestFixtureFactory getFixtureFactory() {
    return ourInstance;
  }

  public abstract TestFixtureBuilder<IdeaProjectTestFixture> createLightFixtureBuilder();

  public abstract JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture);

  public abstract JavaCodeInsightTestFixture createCodeInsightFixture(IdeaProjectTestFixture projectFixture, TempDirTestFixture tempDirFixture);

  //also implicitly initializes ourInstance and registers java module fixture builder
  public static TestFixtureBuilder<IdeaProjectTestFixture> createFixtureBuilder() {
    return IdeaTestFixtureFactoryImpl.getFixtureFactory().createFixtureBuilder();
  }
}
