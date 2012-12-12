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
public class ClassModifierTest extends IncrementalTestCase {
  public ClassModifierTest() throws Exception {
    super("classModifiers");
  }

  public void testAddStatic() throws Exception {
    doTest();
  }

  public void testRemoveStatic() throws Exception {
    doTest();
  }

  public void testDecAccess() throws Exception {
    doTest();
  }

  public void testSetAbstract() throws Exception {
    doTest();
  }

  public void testDropAbstract() throws Exception {
    doTest();
  }

  public void testSetFinal() throws Exception {
    doTest();
  }

  public void testSetFinal1() throws Exception {
    doTest();
  }
}
