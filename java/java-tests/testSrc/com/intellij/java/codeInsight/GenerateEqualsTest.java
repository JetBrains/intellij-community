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
  public void testNoBaseEquals() throws Exception {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testPrimitiveFields() throws Exception {
    doTest(new int[]{0, 1, 2}, new int[0], new int[0], false);
  }

  public void testFields() throws Exception {
    doTest(new int[]{0, 1, 2}, new int[0], new int[]{1}, false);
  }

  public void testAbstractSuperEquals() throws Exception {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testSuperEquals() throws Exception {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testHashCode() throws Exception {
    doTest(new int[]{0, 1, 2, 3}, new int[]{0, 1, 2, 3}, new int[]{1}, false);
  }

  public void testArrays() throws Exception {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }

  public void testOneDoubleField() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testOneFloatField() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testOneField() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testNotNull() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testInsertOverride() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }
  
  public void testLangClass() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  public void testLocalLangClass() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  public void testArraysClass() throws Exception {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

}
