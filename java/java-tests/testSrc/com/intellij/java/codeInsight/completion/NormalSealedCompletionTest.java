// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class NormalSealedCompletionTest extends NormalCompletionTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_LATEST_WITH_LATEST_JDK;
  }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSealedClassDifferentPackageInheritor() {
    myFixture.addClass("package bar;\nimport foo.*;\npublic final class Child2 extends Parent {}");
    doTest();
  }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSealedClassPermitsReference() { doTest(); }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSecondPermitsReference() { doTest(); }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testPermitsReferenceNoPrefix() { 
    configure();
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "Bar", "Foo");
  }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testPermitsReferencePrefix() { doTest("\n"); }

  @NeedsIndex.Full(reason = "AllClassesGetter.processJavaClasses uses indices, see 0a72bf3a7baa7dc1550e8e4308431d78eb753eb6 commit")
  public void testSealedPermitsInner() { doTest("\n"); }

  @NeedsIndex.Full
  public void testNestedClassCompletion() {
    configure();
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "AddUserError.NameIsEmpty", "AddUserError.NameIsTooLong");
  }

  @NeedsIndex.Full
  public void testNestedClassCompletion2() {
    configure();
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "AddUserError.NameIsEmpty", "AddUserError.NameIsEmpty.T4", 
                                             "AddUserError.NameIsTooLong", "AddUserError.NameIsTooLong.T", "AddUserError.NameIsTooLong.T3");
  }

  @NeedsIndex.ForStandardLibrary
  public void testNoAnonymousForSealedInterface() {
    configure();
    myFixture.completeBasic();
    myFixture.assertPreferredCompletionItems(0, "FormatFlagsConversionMismatchException");
  }
}
