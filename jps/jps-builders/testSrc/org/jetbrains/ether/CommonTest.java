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
 * Date: 22.09.11
 */
public class CommonTest extends IncrementalTestCase {
  public CommonTest() throws Exception {
    super("common");
  }

  public void testAnonymous() throws Exception {
    doTest();
  }

  public void testChangeDefinitionToClass() throws Exception {
    doTest();
  }

  public void testChangeDefinitionToClass2() throws Exception {
    doTest();
  }

  public void testDeleteClass() throws Exception {
    doTest();
  }

  public void testDeleteClass1() throws Exception {
    doTest();
  }

  public void testDeleteClass2() throws Exception {
    doTest();
  }

  public void testDeleteClassAfterCompileErrors() throws Exception {
    setupInitialProject();

    doTestBuild(2);
  }

  public void testDeleteClassPackageDoesntMatchRoot() throws Exception {
    doTest();
  }

  public void testInner() throws Exception {
    doTest();
  }

  public void testNoResourceDelete() throws Exception {
    doTest();
  }

  public void testNoSecondFileCompile() throws Exception {
    doTest();
  }

  public void testNoSecondFileCompile1() throws Exception {
    doTest();
  }

  public void testDependencyUpdate() throws Exception {
    doTest();
  }

  public void testClass2Interface1() throws Exception {
    doTest();
  }

  public void testClass2Interface2() throws Exception {
    doTest();
  }

  public void testClass2Interface3() throws Exception {
    doTest();
  }

  public void testDeleteClass3() throws Exception {
    doTest();
  }

  public void testDeleteClass4() throws Exception {
    doTest();
  }

  public void testDeleteInnerClass() throws Exception {
    doTest();
  }

  public void testDeleteInnerClass1() throws Exception {
    doTest();
  }

  public void testAddClass() throws Exception {
    doTest();
  }

  public void testAddDuplicateClass() throws Exception {
    doTest();
  }

  public void testAddClassHidingImportedClass() throws Exception {
    doTest();
  }

  public void testAddClassHidingImportedClass2() throws Exception {
    doTest();
  }

}
