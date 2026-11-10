// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInspection.JavaApiUsageInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public final class ResolveWithLanguageLevelMismatchTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testRecord21() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package com.example; public final class Record {public static int x;}");

    myFixture.configureByText("MyClass.java", """
      import com.example.*;
      
      class MyClass {
        int a = <error descr="Reference to 'Record' is ambiguous, both 'com.example.Record' and 'java.lang.Record' match">Record</error>.x;
      }""");
    myFixture.checkHighlighting();
  }
  
  @Test
  public void testRecord8() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
      myFixture.addClass("package com.example; public final class Record {public static int x;}");

      myFixture.configureByText("MyClass.java", """
      import com.example.*;
      
      class MyClass {
        int a = Record.x;
      }""");
      myFixture.checkHighlighting();
    });
  }
  
  @Test
  public void testRecord8Explicit() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_1_8, () -> {
      myFixture.addClass("package com.example; public final class Record {public static int x;}");

      myFixture.configureByText("MyClass.java", """
      import java.lang.Record;
      import com.example.*;
      
      class MyClass {
        int a = <error descr="Usage of API documented as @since 16+">Record</error>.<error descr="Cannot resolve symbol 'x'">x</error>;
      }""");
      myFixture.enableInspections(new JavaApiUsageInspection());
      myFixture.checkHighlighting();
    });
  }
  
}
