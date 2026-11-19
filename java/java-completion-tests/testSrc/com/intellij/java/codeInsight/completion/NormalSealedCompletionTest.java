// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.completion.LightCompletionFixtureKt.lightCompletionProjectFixture;
import static com.intellij.java.codeInsight.completion.NormalCompletionFixtureKt.normalCompletionFixture;

@TestApplication
@IndexingModeTemplate
public class NormalSealedCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightCompletionProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK);
  private final TestFixture<NormalCompletionFixture> completion = normalCompletionFixture(projectFixture);

  @Test
  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSealedClassDifferentPackageInheritor() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("package bar;\nimport foo.*;\npublic final class Child2 extends Parent {}");
      completion.get().doTest();
    });
  }

  @Test
  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSealedClassPermitsReference() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSecondPermitsReference() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest()); }

  @Test
  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testPermitsReferenceNoPrefix() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture(); 
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "Bar", "Foo");
    });
  }

  @Test
  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testPermitsReferencePrefix() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSealedPermitsInner() { EdtTestUtil.runInEdtAndWait(() -> completion.get().doTest("\n")); }

  @Test
  @NeedsIndex.Full
  public void testNestedClassCompletion() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "AddUserError.NameIsEmpty", "AddUserError.NameIsTooLong");
    });
  }

  @Test
  @NeedsIndex.Full
  public void testNestedClassCompletion2() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "AddUserError.NameIsEmpty", "AddUserError.NameIsEmpty.T4", 
                                               "AddUserError.NameIsTooLong", "AddUserError.NameIsTooLong.T", "AddUserError.NameIsTooLong.T3");
    });
  }

  @Test
  @NeedsIndex.ForStandardLibrary
  public void testNoAnonymousForSealedInterface() {
    LightJavaCodeInsightTestFixture myFixture = completion.get().getFixture();
    EdtTestUtil.runInEdtAndWait(() -> {
      completion.get().configure();
      myFixture.completeBasic();
      myFixture.assertPreferredCompletionItems(0, "FormatFlagsConversionMismatchException");
    });
  }
}
