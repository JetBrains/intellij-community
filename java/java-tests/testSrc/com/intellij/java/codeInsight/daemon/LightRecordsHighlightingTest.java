// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightRecordsHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingRecords";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14;
  }

  public void testRecordBasics() {
    doTest();
  }
  public void testRecordAccessors() {
    doTest();
  }
  public void testRecordAccessorsJava15() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15_PREVIEW, this::doTest);
  }
  public void testRecordConstructors() {
    doTest();
  }
  public void testRecordConstructorAccessJava15() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15_PREVIEW, this::doTest);
  }
  public void testRecordCompactConstructors() {
    doTest();
  }
  public void testRecordCompactConstructorsJava15() {
    IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_15_PREVIEW, this::doTest);
  }
  public void testLocalRecords() {
    doTest();
  }

  public void testRenameOnRecordComponent() {
    doTestRename();
  }

  public void testRenameOnCompactConstructorReference() {
    doTestRename();
  }

  public void testRenameOnExplicitGetter() {
    doTestRename();
  }

  public void testRenameWithCanonicalConstructor() {
    doTestRename();
  }

  public void testRenameGetterOverloadPresent() {
    doTestRename();
  }

  private void doTestRename() {
    doTest();
    myFixture.renameElementAtCaret("baz");
    myFixture.checkResultByFile(getTestName(false) + "_after.java");
  }

  private void doTest() {
    myFixture.addClass("package java.lang; public abstract class Record {" +
                       "public abstract boolean equals(Object obj);" +
                       "public abstract int hashCode();" +
                       "public abstract String toString();" +
                       "}");
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}