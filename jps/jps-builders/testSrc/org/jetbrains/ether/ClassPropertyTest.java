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

/**
 * @author: db
 * Date: 09.08.11
 */
public class ClassPropertyTest extends IncrementalTestCase {
  public ClassPropertyTest() throws Exception {
    super("classProperties");
  }

  public void testAddExtends() throws Exception {
    doTest();
  }

  public void testAddImplements() throws Exception {
    doTest();
  }

  public void testChangeExtends() throws Exception {
    doTest();
  }

  public void testRemoveExtends() throws Exception {
    doTest();
  }

  public void testRemoveExtendsAffectsFieldAccess() throws Exception {
    doTest();
  }

  public void testRemoveExtendsAffectsMethodAccess() throws Exception {
    doTest();
  }

  public void testRemoveImplements() throws Exception {
    doTest();
  }

  public void testRemoveImplements2() throws Exception {
    doTest();
  }

  public void testRemoveImplements3() throws Exception {
    doTest();
  }

  public void testChangeExtends2() throws Exception {
      doTest();
  }
}
