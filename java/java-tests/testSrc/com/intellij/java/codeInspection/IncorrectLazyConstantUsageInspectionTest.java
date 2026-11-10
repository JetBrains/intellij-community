// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.IncorrectLazyConstantUsageInspection;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.style.FieldMayBeFinalInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class IncorrectLazyConstantUsageInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_26);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testIncorrectLazyConstantUsage() {
    doTest();
  }

  @Test
  public void testIncorrectLazyConstantUsageFix() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.launchAction(myFixture.findSingleIntention("Make 'f' 'final'"));
    myFixture.checkResultByFile(name + "_after.java");
  }

  @Test
  public void testNoDuplicateWithFieldMayBeFinal() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new FieldMayBeFinalInspection());
    doTest();
  }

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/incorrectLazyConstantUsage");
    myFixture.addClass("""
                         package java.lang;
                         import java.util.function.Supplier;
                         
                         public final class LazyConstant<V> {
                             public static <V> LazyConstant<V> of(Supplier<? extends V> supplier) {
                                 return null;
                             }
                         
                             T get();
                         }
                         """);
    myFixture.enableInspections(new IncorrectLazyConstantUsageInspection());
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.testHighlighting(true, false, false, myFixture.getTestName(false) + ".java");
  }
}
