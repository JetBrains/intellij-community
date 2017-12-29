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
 */
public class MemberChangeTest extends IncrementalTestCase {
  public MemberChangeTest() {
    super("membersChange");
  }

  public void testAddAbstractMethod() {
    doTest();
  }

  public void testAddPrivateMethodToAbstractClass() {
    doTest();
  }

  public void testAddConstructorParameter() {
    doTest();
  }

  public void testAddFieldToBaseClass() {
    doTest();
  }

  public void testAddFieldToDerived() {
    doTest();
  }

  public void testAddFieldToInterface() {
    doTest();
  }

  public void testAddFieldToInterface2() {
    doTest();
  }

  public void testAddFinalMethodHavingNonFinalMethodInSubclass() {
    doTest();
  }

  public void testAddHidingField() {
    doTest();
  }

  public void testAddHidingMethod() {
    doTest();
  }

  public void testAddInterfaceMethod() {
    doTest();
  }

  public void testAddInterfaceMethod2() {
    doTest();
  }

  public void testAddLessAccessibleFieldToDerived() {
    doTest();
  }

  public void testAddMethodWithIncompatibleReturnType() {
    doTest();
  }

  public void testAddMethodWithCovariantReturnType() {
    doTest();
  }

  public void testAddMoreAccessibleMethodToBase() {
    doTest();
  }

  public void testAddMoreSpecific() {
    doTest();
  }

  public void testAddMoreSpecific1() {
    doTest();
  }

  public void testAddMoreSpecific2() {
    doTest();
  }

  public void testAddNonStaticMethodHavingStaticMethodInSubclass() {
    doTest();
  }

  public void testAddStaticFieldToDerived() {
    doTest();
  }

  public void testChangeStaticMethodSignature() {
    doTest();
  }

  public void testChangeMethodGenericReturnType() {
    doTest();
  }

  public void testDeleteConstructor() {
    doTest();
  }

  public void testAddParameterToConstructor() {
    doTest();
  }

  public void testDeleteInner() {
    doTest();
  }

  public void testDeleteMethod() {
    doTest();
  }

  public void testDeleteInterfaceMethod() {
    doTest();
  }

  public void testDeleteMethodImplementation() {
    doTest();
  }

  public void testDeleteMethodImplementation2() {
    doTest();
  }

  public void testDeleteMethodImplementation3() {
    doTest();
  }

  public void testDeleteMethodImplementation4() {
    doTest();
  }

  public void testDeleteMethodImplementation5() {
    doTest();
  }

  public void testDeleteMethodImplementation6() {
    doTest();
  }

  public void testDeleteMethodImplementation7() {
    doTest();
  }

  public void testHierarchy() {
    doTest();
  }

  public void testHierarchy2() {
    doTest();
  }

  public void testRemoveBaseImplementation() {
    doTest();
  }

  public void testRemoveHidingField() {
    doTest();
  }

  public void testRemoveHidingMethod() {
    doTest();
  }

  public void testRemoveMoreAccessibleMethod() {
    doTest();
  }

  public void testRenameMethod() {
    doTest();
  }

  public void testMoveMethodToSubclass() {
    doTest().assertSuccessful();
  }
  
  public void testThrowsListDiffersInBaseAndDerived() {
    doTest();
  }

  public void testRemoveThrowsInBaseMethod() {
    doTest();
  }

  public void testAddMethod() {
    doTest();
  }

  public void testAddVarargMethod() {
    doTest();
  }
}
