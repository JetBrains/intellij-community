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
  public AnnotationTest() throws Exception {
    super("annotations");
  }

  public void testAddAnnotationTarget() throws Exception {
    doTest();
  }

  public void testAddAnnotationTypeMemberWithDefaultValue() throws Exception {
    doTest();
  }

  public void testAddAnnotationTypeMemberWithDefaultValue2() throws Exception {
    doTest();
  }

  public void testAddAnnotationTypeMemberWithoutDefaultValue() throws Exception {
    doTest();
  }

  public void testAddDefaultToAnnotationMember() throws Exception {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy() throws Exception {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy1() throws Exception {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy2() throws Exception {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy3() throws Exception {
    doTest();
  }

  public void testChangeAnnotationRetentionPolicy4() throws Exception {
    doTest();
  }

  public void testChangeAnnotationTypeMemberType() throws Exception {
    doTest();
  }

  public void testChangeAnnotationTypeMemberTypeArray() throws Exception {
    doTest();
  }

  public void testChangeAnnotationTypeMemberTypeEnumArray() throws Exception {
    doTest();
  }

  public void testClassAsArgument() throws Exception {
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

  public void testRemoveAnnotationTarget() throws Exception {
    doTest();
  }

  public void testRemoveAnnotationTypeMember() throws Exception {
    doTest();
  }

  public void testRemoveAnnotationTypeMember1() throws Exception {
    doTest();
  }

  public void testRemoveDefaultFromAnnotationMember() throws Exception {
    doTest();
  }

  public void testConservativeNonIncremental() throws Exception {
    doTest();
  }

  public void testConservativeNonIncremental1() throws Exception {
    doTest();
  }

  public void testAnnotationsTracker() throws Exception {
    doTest();
  }
}
