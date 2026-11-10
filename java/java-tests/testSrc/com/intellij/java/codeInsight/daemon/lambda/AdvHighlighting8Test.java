/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class AdvHighlighting8Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + BASE_PATH);
  }

  @NonNls private static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/advHighlighting8";

  @Test
  public void testProtectedVariable() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package p1;
                         public class A {
                           protected String myFoo = "A";
                         }""");
    doTest();
  }

  @Test
  public void testIDEA67842() {
    doTest();
  }

  @Test
  public void testUnrelatedConcreteInConstructors() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package p;
                         import java.util.List;

                         public class A {
                           public A(List l) {
                           }
                         }""");
    myFixture.addClass("""
                         import java.util.List;
                         public class A<T> extends p.A {
                           public A(List<T> l) {
                             super(l);
                           }
                         }""");
    doTest();
  }

  @Test
  public void testPackageLocalMethod() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package foo;
                         public abstract class A {
                           abstract void foo();
                         }""");
    myFixture.addClass("""
                         package foo.bar;
                         import foo.A;
                         abstract class B extends A {}""");
    doTest();
  }

  @Test
  public void testPackagePrivateAndSuperMethodReference() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a;
                         public class A {
                             protected void foo(int a) {
                                 System.out.println(a);
                             }
                         }""");
    doTest();
  }
  
  @Test
  public void testTooltipProperlyEscaped() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestName(false) + ".java");
    List<HighlightInfo> infos = myFixture.doHighlighting(HighlightSeverity.ERROR);
    assertEquals(1, infos.size());
    assertEquals(
      "<html>'unmodifiableSet(java.util.Set&lt;? extends java.lang.String&gt;)' in 'java.util.Collections' cannot be applied to '(java.util.TreeSet&lt;java.lang.String&gt;)'</html>",
      infos.get(0).getToolTip());
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(false, false, false, myFixture.getTestName(false) + ".java");
  }
}
