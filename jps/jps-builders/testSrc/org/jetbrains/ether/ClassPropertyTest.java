/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.ether;

public class ClassPropertyTest extends IncrementalTestCase {
  public ClassPropertyTest() {
    super("classProperties");
  }

  public void testAddExtends() {
    doTest();
  }

  public void testAddImplements() {
    doTest();
  }

  public void testChangeExtends() {
    doTest();
  }

  public void testRemoveExtends() {
    doTest();
  }

  public void testRemoveExtendsAffectsFieldAccess() {
    doTest();
  }

  public void testRemoveExtendsAffectsMethodAccess() {
    doTest();
  }

  public void testRemoveImplements() {
    doTest();
  }

  public void testRemoveImplements2() {
    doTest();
  }

  public void testRemoveImplements3() {
    doTest();
  }

  public void testChangeExtends2() {
      doTest();
  }

  public void testConvertToCheckedException() {
      doTest();
  }
}
