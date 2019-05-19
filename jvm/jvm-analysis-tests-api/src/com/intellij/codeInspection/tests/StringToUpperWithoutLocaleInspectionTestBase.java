package com.intellij.codeInspection.tests;

import com.intellij.codeInspection.StringToUpperWithoutLocale2Inspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;

public abstract class StringToUpperWithoutLocaleInspectionTestBase extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.enableInspections(new StringToUpperWithoutLocale2Inspection());
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
    moduleBuilder.addLibrary("annotations", PathUtil.getJarPathForClass(NonNls.class));
  }
}
