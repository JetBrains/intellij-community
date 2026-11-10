// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public final class ModifierFixtureTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testUnreachableFunctionalTypeFromLambda() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package one;
                         public interface Supplier<T> {
                             T get();
                         }
                         """);
    myFixture.addClass("""
                         package one;
                         class M1 {} // note package-private
                         """);
    myFixture.addClass("""
                         package one;
                         
                         public class M2 {
                             public static void test(Supplier<M1> supplier) {
                                 System.out.println(supplier.get());
                             }
                         
                             public static M1Sub create() {
                                 return new M1Sub();
                             }
                         
                             public static class M1Sub extends M1 {
                         
                             }
                         }
                         """);
    myFixture.configureByText("M3.java", """
      package two;
      
      import one.*;
      
      public class M3 {
          public static void main(String[] args) {
              M2.test(() -> M2.<caret>create());
          }
      }
      """);
    myFixture.findSingleIntention("Make 'M1' public");
  }

}
