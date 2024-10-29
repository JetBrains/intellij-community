// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInspection.JavaApiUsageInspection;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public final class ResolveWithLanguageLevelMismatchTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testRecord21() {
    myFixture.addClass("package com.example; public final class Record {public static int x;}");

    myFixture.configureByText("MyClass.java", """
      import com.example.*;
      
      class MyClass {
        int a = <error descr="Reference to 'Record' is ambiguous, both 'com.example.Record' and 'java.lang.Record' match">Record</error>.x;
      }""");
    myFixture.checkHighlighting();
  }
  
  public void testRecord8() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> {
      myFixture.addClass("package com.example; public final class Record {public static int x;}");

      myFixture.configureByText("MyClass.java", """
      import com.example.*;
      
      class MyClass {
        int a = Record.x;
      }""");
      myFixture.checkHighlighting();
    });
  }
  
  public void testRecord8Explicit() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_1_8, () -> {
      myFixture.addClass("package com.example; public final class Record {public static int x;}");

      myFixture.configureByText("MyClass.java", """
      import java.lang.Record;
      import com.example.*;
      
      class MyClass {
        int a = <error descr="Usage of API documented as @since 14+">Record</error>.<error descr="Cannot resolve symbol 'x'">x</error>;
      }""");
      myFixture.enableInspections(new JavaApiUsageInspection());
      myFixture.checkHighlighting();
    });
  }
  
}
