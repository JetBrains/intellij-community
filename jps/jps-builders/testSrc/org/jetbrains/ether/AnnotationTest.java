// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ether;

import org.jetbrains.jps.builders.java.JavaBuilderUtil;
import org.junit.jupiter.api.Test;

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

  @Test
  public void testAddAnnotationTarget() {
    doTest();
  }

  @Test
  public void testAddAnnotationTargetTypeUse() {
    doTest();
  }
  
  @Test
  public void testAddTypeUseAnnotationTarget() {
    doTest();
  }

  @Test
  public void testAddRecordComponentAnnotationTarget() {
    doTest();
  }

  @Test
  public void testAddAnnotationTypeMemberWithDefaultValue() {
    doTest();
  }

  @Test
  public void testAddAnnotationTypeMemberWithDefaultValue2() {
    doTest();
  }

  @Test
  public void testAddAnnotationTypeMemberWithoutDefaultValue() {
    doTest();
  }

  @Test
  public void testAddDefaultToAnnotationMember() {
    doTest();
  }

  @Test
  public void testChangeAnnotationRetentionPolicy() {
    doTest();
  }

  @Test
  public void testChangeAnnotationRetentionPolicy1() {
    doTest();
  }

  @Test
  public void testChangeAnnotationRetentionPolicy2() {
    doTest();
  }

  @Test
  public void testChangeAnnotationRetentionPolicy3() {
    doTest();
  }

  @Test
  public void testChangeAnnotationRetentionPolicy4() {
    doTest();
  }

  @Test
  public void testChangeAnnotationTypeMemberType() {
    doTest();
  }

  @Test
  public void testChangeAnnotationTypeMemberTypeArray() {
    doTest();
  }

  @Test
  public void testChangeAnnotationTypeMemberTypeEnumArray() {
    doTest();
  }

  @Test
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

  @Test
  public void testRemoveAnnotationTarget() {
    doTest();
  }

  @Test
  public void testRemoveTypeUseAnnotationTarget() {
    doTest();
  }

  @Test
  public void testRemoveAnnotationTypeMember() {
    doTest();
  }

  @Test
  public void testRemoveAnnotationTypeMember1() {
    doTest();
  }

  @Test
  public void testRemoveDefaultFromAnnotationMember() {
    doTest();
  }

  @Test
  public void testConservativeNonIncremental() {
    doTest();
  }

  @Test
  public void testConservativeNonIncremental1() {
    doTest();
  }

  @Test
  public void testAnnotationsTracker() {
    doTest();
  }
}
