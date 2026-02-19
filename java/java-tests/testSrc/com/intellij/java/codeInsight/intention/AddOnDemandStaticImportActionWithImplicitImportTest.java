// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.intention;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class AddOnDemandStaticImportActionWithImplicitImportTest extends LightJavaCodeInsightFixtureTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_23;
  }

  public void testImplicitImportIO() {
    myFixture.addClass("""
                         package java.io;
                         
                         public final class IO {
                           public static void println(Object obj) {}
                         }
                         """);
    myFixture.configureByText("a.java", """
      public static void main(String[] args) {
          IO<caret>.println("1");
      }""");
    myFixture.launchAction(myFixture.findSingleIntention("Add on-demand static import for 'java.io.IO'"));
    myFixture.checkResult("""
                            public static void main(String[] args) {
                                println("1");
                            }""");
  }
}

