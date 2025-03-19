// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;

import java.util.Set;

public class AnnotationTest extends IncrementalTestCase {
  private static final Set<String> GRAPH_ONLY_TESTS = Set.of("annotationsTracker");

  public AnnotationTest() {
    super("annotations");
  }

  @Override
  protected boolean shouldRunTest() {
    if (JavaBuilderUtil.isDepGraphEnabled()) {
      return super.shouldRunTest();
    }
    return !GRAPH_ONLY_TESTS.contains(getTestName(true));
  }

  public void testAddAnnotationTarget() {
    doTest();
  }

  public void testAddAnnotationTargetTypeUse() {
    doTest();
  }
  
  public void testAddTypeUseAnnotationTarget() {
    doTest();
  }

  public void testAddRecordComponentAnnotationTarget() {
    doTest();
  }

  public void testAddAnnotationTypeMemberWithDefaultValue() {
    doTest();
  }

  public void testAddAnnotationTypeMemberWithDefaultValue2() {
    doTest();
  }

  public void testAddAnnotationTypeMemberWithoutDefaultValue() {
    doTest();
  }

  public void testAddDefaultToAnnotationMember() {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy() {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy1() {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy2() {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy3() {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy4() {
    doTest();
  }

  public void testChangeAnnotationTypeMemberType() {
    doTest();
  }

  public void testChangeAnnotationTypeMemberTypeArray() {
    doTest();
  }

  public void testChangeAnnotationTypeMemberTypeEnumArray() {
    doTest();
  }

  public void testClassAsArgument() {
    doTest();
  }

  /*  Discussion is needed --- unnecessarily conservative
 public void testMetaAnnotationChanged() throws Exception {
     doTest();
 }

 public void testMetaAnnotationChangedCascade() throws Exception {
     doTest();
 }

 public void testMetaAnnotationChangedCascade2() throws Exception {
     doTest();
 } */

  public void testRemoveAnnotationTarget() {
    doTest();
  }

  public void testRemoveTypeUseAnnotationTarget() {
    doTest();
  }

  public void testRemoveAnnotationTypeMember() {
    doTest();
  }

  public void testRemoveAnnotationTypeMember1() {
    doTest();
  }

  public void testRemoveDefaultFromAnnotationMember() {
    doTest();
  }

  public void testConservativeNonIncremental() {
    doTest();
  }

  public void testConservativeNonIncremental1() {
    doTest();
  }

  public void testAnnotationsTracker() {
    doTest();
  }
}
