/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.refactoring;

public class FindMethodDuplicatesMiscTest extends FindMethodDuplicatesBaseTest {
  @Override
  protected String getTestFilePath() {
    return "/refactoring/methodDuplicatesMisc/" + getTestName(false) + ".java";
  }

  public void testChangeReturnTypeByParameter() throws Exception {
    doTest();
  }

  public void testChangeReturnTypeByField() throws Exception {
    doTest();
  }

  public void testMethodTypeParameters() throws Exception {
    doTest();
  }

  public void testChangeReturnTypeByReturnExpression() throws Exception {
    doTest();
  }

  public void testChangeReturnTypeByReturnValue() throws Exception {
    doTest();
  }

  public void testParametersModification() throws Exception {
    doTest();
  }

  public void testPassArray2VarargMethodCall() throws Exception {
    doTest();
  }

  public void testDetectNameConflicts() throws Exception {
    doTest();
  }

  public void testNoDetectNameConflicts() throws Exception {
    doTest();
  }

  public void testDetectNameConflictsWithStatic() throws Exception {
    doTest();
  }

  public void testCorrectThis() throws Exception {
    doTest();
  }

  public void testSuperInTheSameContext() throws Exception {
    doTest(false);
  }

  public void testSuperInTheSameContextQualified() throws Exception {
    doTest();
  }

  public void testInsertSuperQualifierWhenNameConflicts() throws Exception {
    doTest();
  }

  public void testUnqualifiedStaticAccess() throws Exception {
    doTest();
  }

  public void testCandidateUnqualifiedStaticAccess() throws Exception {
    doTest();
  }

  public void testVarargsAccess() throws Exception {
    doTest();
  }

  public void testIncorrectVarargsAccess() throws Exception {
    doTest();
  }

  public void testVarVarargsAccess() throws Exception {
    doTest();
  }

  public void testSkipNotAccessible() throws Exception {
    doTest(false);
  }
  
  public void testQualifiers() throws Exception {
    doTest();
  }

  public void testSimpleConstant() throws Exception {
    doTest();
  }

  public void testAnonymousInitializer() throws Exception {
    doTest();
  }

  public void testReplaceDuplicateInsideAnonymous() throws Exception {
    doTest();
  }

  public void testMakeStaticWhenUsedInInheritor() throws Exception {
    doTest();
  }
}