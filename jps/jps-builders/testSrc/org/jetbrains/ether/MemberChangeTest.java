// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.junit.jupiter.api.Test;

import java.util.Set;

public class MemberChangeTest extends IncrementalTestCase {
  private static final Set<String> GRAPH_ONLY_TESTS =
    Set.of("addLambdaTargetMethod", "addLambdaTargetMethod2", "addLambdaTargetMethodNoRecompile", "addPackagePrivateMethodToParentClashingWithMethodFromInterface");

  public MemberChangeTest() {
    super("membersChange");
  }

  @Override
  protected boolean shouldRunTest() {
    if (JavaBuilderUtil.isDepGraphEnabled()) {
      return super.shouldRunTest();
    }
    return !GRAPH_ONLY_TESTS.contains(getTestName(true));
  }

  @Test
  public void testAddAbstractMethod() {
    doTest();
  }

  @Test
  public void testAddSAMInterfaceMethod() {
    doTest();
  }

  @Test
  public void testDeleteSAMInterfaceMethod() {
    doTest();
  }

  @Test
  public void testRenameSAMInterfaceMethod() {
    doTest();
  }

  @Test
  public void testAddSAMInterfaceAbstractMethod() {
    doTest();
  }

  @Test
  public void testChangeSAMInterfaceMethodToAbstract() {
    doTest();
  }

  @Test
  public void testAddLambdaTargetMethod() {
    doTest();
  }

  @Test
  public void testAddLambdaTargetMethod2() {
    doTest();
  }

  @Test
  public void testAddLambdaTargetMethodNoRecompile() {
    doTest();
  }

  @Test
  public void testAddPrivateMethodToAbstractClass() {
    doTest();
  }

  @Test
  public void testAddConstructorParameter() {
    doTest();
  }

  @Test
  public void testAddFieldToBaseClass() {
    doTest();
  }

  @Test
  public void testAddFieldOfSameKindToBaseClass() {
    doTest();
  }

  @Test
  public void testAddFieldToDerived() {
    doTest();
  }

  @Test
  public void testAddFieldToInterface() {
    doTest();
  }

  @Test
  public void testAddFieldToEnum() {
    doTest();
  }

  @Test
  public void testAddFieldToInterface2() {
    doTest();
  }

  @Test
  public void testAddFinalMethodHavingNonFinalMethodInSubclass() {
    doTest();
  }

  @Test
  public void testAddHidingField() {
    doTest();
  }

  @Test
  public void testAddHidingMethod() {
    doTest();
  }

  @Test
  public void testAddInterfaceMethod() {
    doTest();
  }

  @Test
  public void testAddInterfaceMethod2() {
    doTest();
  }

  @Test
  public void testAddLessAccessibleFieldToDerived() {
    doTest();
  }

  @Test
  public void testAddMethodWithIncompatibleReturnType() {
    doTest();
  }

  @Test
  public void testAddMethodWithCovariantReturnType() {
    doTest();
  }

  @Test
  public void testAddMoreAccessibleMethodToBase() {
    doTest();
  }

  @Test
  public void testAddMoreSpecific() {
    doTest();
  }

  @Test
  public void testAddMoreSpecific1() {
    doTest();
  }

  @Test
  public void testAddMoreSpecific2() {
    doTest();
  }

  @Test
  public void testAddNonStaticMethodHavingStaticMethodInSubclass() {
    doTest();
  }

  @Test
  public void testAddStaticFieldToDerived() {
    doTest();
  }

  @Test
  public void testChangeStaticMethodSignature() {
    doTest();
  }

  @Test
  public void testChangeMethodGenericReturnType() {
    doTest();
  }

  @Test
  public void testDeleteConstructor() {
    doTest();
  }

  @Test
  public void testAddParameterToConstructor() {
    doTest();
  }

  @Test
  public void testDeleteInner() {
    doTest();
  }

  @Test
  public void testDeleteMethod() {
    doTest();
  }

  @Test
  public void testDeleteInterfaceMethod() {
    doTest();
  }

  @Test
  public void testDeleteMethodImplementation() {
    doTest();
  }

  @Test
  public void testDeleteMethodImplementation2() {
    doTest();
  }

  @Test
  public void testDeleteMethodImplementation3() {
    doTest();
  }

  @Test
  public void testDeleteMethodImplementation4() {
    doTest();
  }

  @Test
  public void testDeleteMethodImplementation5() {
    doTest();
  }

  @Test
  public void testDeleteMethodImplementation6() {
    doTest();
  }

  @Test
  public void testDeleteMethodImplementation7() {
    doTest();
  }

  @Test
  public void testDeleteOverridingPackageLocalMethodImpl() {
    doTest().assertFailed(); // should fail because of javac bug
  }

  @Test
  public void testDeleteOverridingPackageLocalMethodImpl2() {
    doTest().assertFailed();
  }

  @Test
  public void testHierarchy() {
    doTest();
  }

  @Test
  public void testHierarchy2() {
    doTest();
  }

  @Test
  public void testRemoveBaseImplementation() {
    doTest();
  }

  @Test
  public void testRemoveHidingField() {
    doTest();
  }

  @Test
  public void testRemoveHidingMethod() {
    doTest();
  }

  @Test
  public void testRemoveMoreAccessibleMethod() {
    doTest();
  }

  @Test
  public void testRenameMethod() {
    doTest();
  }

  @Test
  public void testMoveMethodToSubclass() {
    doTest().assertSuccessful();
  }
  
  @Test
  public void testThrowsListDiffersInBaseAndDerived() {
    doTest();
  }

  @Test
  public void testRemoveThrowsInBaseMethod() {
    doTest();
  }

  @Test
  public void testAddMethod() {
    doTest();
  }

  @Test
  public void testAddOverloadingMethod() {
    doTest();
  }

  @Test
  public void testAddOverridingMethodAndChangeReturnType() {
    doTest();
  }

  @Test
  public void testAddOverloadingConstructor() {
    doTest();
  }

  @Test
  public void testAddVarargMethod() {
    doTest();
  }

  @Test
  public void testReplaceMethodWithBridge() {
    doTest();
  }

  @Test
  public void testPushFieldDown() {
    doTest();
  }

  @Test
  public void testAddPackagePrivateMethodToParentClashingWithMethodFromInterface() {
    doTest().assertFailed();
  }
}
