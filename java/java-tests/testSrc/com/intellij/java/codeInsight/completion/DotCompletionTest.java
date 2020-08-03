// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightCompletionTestCase;
import com.intellij.testFramework.NeedsIndex;
import org.jetbrains.annotations.NotNull;

public class DotCompletionTest extends LightCompletionTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/dot/";
  }

  public void testInstance() {
    configureByFile("Dot1.java");
    assertEquals("", myPrefix);
    assertContainsItems("a", "foo");
  }

  public void testClass() {
    configureByFile("Dot2.java");
    assertEquals("", myPrefix);
    assertContainsItems("a", "foo");
  }

  public void testAnonymous() {
    configureByFile("Dot3.java");
    assertEquals("", myPrefix);
    assertContainsItems("a", "foo");
  }

  public void testShowStatic() {
    configureByFile("Dot4.java");
    assertEquals("", myPrefix);
    assertContainsItems("foo");
    assertNotContainItems("a");
  }

  public void testImports() {
    configureByFile("Dot5.java");
    assertContainsItems("util", "lang");
  }

  @NeedsIndex.ForStandardLibrary
  public void testArrayElement() {
    configureByFile("Dot6.java");
    assertContainsItems("toString", "substring");
  }

  public void testArray() {
    configureByFile("Dot7.java");
    assertContainsItems("clone", "length");
  }

  public void testDuplicatesFromInheritance() {
    configureByFile("Dot8.java");
    assertContainsItems("toString");
  }

  public void testConstructorExclusion() {
    configureByFile("Dot9.java");
    assertContainsItems("foo");
    assertNotContainItems("A");
  }

  public void testPrimitiveArray() {
    configureByFile("Dot10.java");
    assertContainsItems("clone", "length");
  }

  public void testThisExpression() {
    configureByFile("Dot11.java");
    assertContainsItems("foo", "foo1");
  }

  public void testSuperExpression() {
    configureByFile("Dot12.java");
    assertContainsItems("foo");
    assertNotContainItems("foo1");
  }

  @NeedsIndex.ForStandardLibrary
  public void testMultiCatch() {
    configureByFile("MultiCatch.java");
    assertContainsItems("i", "addSuppressed", "getMessage", "printStackTrace");
  }
}
