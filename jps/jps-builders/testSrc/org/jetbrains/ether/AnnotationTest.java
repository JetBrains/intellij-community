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
 * Date: 26.07.11
 */
public class AnnotationTest extends IncrementalTestCase {
  public AnnotationTest() {
    super("annotations");
  }

  public void testAddAnnotationTarget() {
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
