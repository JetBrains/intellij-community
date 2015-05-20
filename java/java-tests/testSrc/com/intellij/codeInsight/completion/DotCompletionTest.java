/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.completion;

import com.intellij.JavaTestUtil;
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

  public void testInstance() throws Exception {
    configureByFile("Dot1.java");
    assertEquals("", myPrefix);
    assertContainsItems("a", "foo");
  }

  public void testClass() throws Exception {
    configureByFile("Dot2.java");
    assertEquals("", myPrefix);
    assertContainsItems("a", "foo");
  }

  public void testAnonymous() throws Exception {
    configureByFile("Dot3.java");
    assertEquals("", myPrefix);
    assertContainsItems("a", "foo");
  }

  public void testShowStatic() throws Exception {
    configureByFile("Dot4.java");
    assertEquals("", myPrefix);
    assertContainsItems("foo");
    assertNotContainItems("a");
  }

  public void testImports() throws Exception {
    configureByFile("Dot5.java");
    assertContainsItems("util", "lang");
  }

  public void testArrayElement() throws Exception {
    configureByFile("Dot6.java");
    assertContainsItems("toString", "substring");
  }

  public void testArray() throws Exception {
    configureByFile("Dot7.java");
    assertContainsItems("clone", "length");
  }

  public void testDuplicatesFromInheritance() throws Exception {
    configureByFile("Dot8.java");
    assertContainsItems("toString");
  }

  public void testConstructorExclusion() throws Exception {
    configureByFile("Dot9.java");
    assertContainsItems("foo");
    assertNotContainItems("A");
  }

  public void testPrimitiveArray() throws Exception {
    configureByFile("Dot10.java");
    assertContainsItems("clone", "length");
  }

  public void testThisExpression() throws Exception {
    configureByFile("Dot11.java");
    assertContainsItems("foo", "foo1");
  }

  public void testSuperExpression() throws Exception {
    configureByFile("Dot12.java");
    assertContainsItems("foo");
    assertNotContainItems("foo1");
  }

  public void testMultiCatch() throws Exception {
    configureByFile("MultiCatch.java");
    assertContainsItems("i", "addSuppressed", "getMessage", "printStackTrace");
  }
}
