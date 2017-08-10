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
package com.intellij.java.codeInsight.completion;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.completion.LightCompletionTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author ik
 * Date: 21.01.2003
 */
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

  public void testMultiCatch() {
    configureByFile("MultiCatch.java");
    assertContainsItems("i", "addSuppressed", "getMessage", "printStackTrace");
  }
}
