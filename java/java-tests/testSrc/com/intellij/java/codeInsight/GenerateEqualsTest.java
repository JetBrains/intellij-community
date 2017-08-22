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
package com.intellij.java.codeInsight;

/**
 * @author dsl
 */
public class GenerateEqualsTest extends GenerateEqualsTestCase {
  public void testNoBaseEquals() {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testPrimitiveFields() {
    doTest(new int[]{0, 1, 2}, new int[0], new int[0], false);
  }

  public void testFields() {
    doTest(new int[]{0, 1, 2}, new int[0], new int[]{1}, false);
  }

  public void testAbstractSuperEquals() {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testSuperEquals() {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testHashCode() {
    doTest(new int[]{0, 1, 2, 3}, new int[]{0, 1, 2, 3}, new int[]{1}, false);
  }

  public void testArrays() {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }

  public void testOneDoubleField() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testOneFloatField() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testOneField() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testNotNull() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testInsertOverride() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }
  
  public void testLangClass() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  public void testLocalLangClass() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  public void testArraysClass() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

}
