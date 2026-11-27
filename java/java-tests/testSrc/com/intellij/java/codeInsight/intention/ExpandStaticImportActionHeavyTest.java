// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;

@TestApplication
public class ExpandStaticImportActionHeavyTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture =
    javaModuleCodeInsightFixture(JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/expandStaticImportHeavy");

  @Test
  public void testPackageInfo() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a;
                         
                         public interface Interfaces {
                             @interface SomeInterface {
                         
                             }
                         }""");
    final String name = "package-info";
    EdtTestUtil.runInEdtAndWait(() -> CodeInsightTestUtil.doIntentionTest(myFixture, JavaBundle.message("intention.text.replace.static.import.with.qualified.access.to.0", "Interfaces"), name + ".java", name + "_after.java"));
  }

  @Test
  public void testPackageInfoTrailingComment() {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
                         package a;

                         public interface Interfaces {
                             @interface SomeInterface {

                             }
                         }""");
    final String name = "packageInfoTrailingComment";
    EdtTestUtil.runInEdtAndWait(() -> CodeInsightTestUtil.doIntentionTest(myFixture, JavaBundle.message("intention.text.replace.static.import.with.qualified.access.to.0", "Interfaces"), name + ".java", name + "_after.java"));
  }
}
