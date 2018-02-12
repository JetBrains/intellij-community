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

  public void testChangeReturnTypeByParameter() {
    doTest();
  }

  public void testChangeReturnTypeByField() {
    doTest();
  }

  public void testMethodTypeParameters() {
    doTest();
  }

  public void testChangeReturnTypeByReturnExpression() {
    doTest();
  }

  public void testChangeReturnTypeByReturnValue() {
    doTest();
  }

  public void testParametersModification() {
    doTest();
  }

  public void testPassArray2VarargMethodCall() {
    doTest();
  }

  public void testDetectNameConflicts() {
    doTest();
  }

  public void testNoDetectNameConflicts() {
    doTest();
  }

  public void testDetectNameConflictsWithStatic() {
    doTest();
  }

  public void testCorrectThis() {
    doTest();
  }

  public void testSuperInTheSameContext() {
    doTest(false);
  }

  public void testSuperInTheSameContextQualified() {
    doTest();
  }

  public void testInsertSuperQualifierWhenNameConflicts() {
    doTest();
  }

  public void testUnqualifiedStaticAccess() {
    doTest();
  }

  public void testCandidateUnqualifiedStaticAccess() {
    doTest();
  }

  public void testVarargsAccess() {
    doTest();
  }

  public void testIncorrectVarargsAccess() {
    doTest();
  }

  public void testVarVarargsAccess() {
    doTest();
  }

  public void testSkipNotAccessible() {
    doTest(false);
  }
  
  public void testQualifiers() {
    doTest();
  }

  public void testSimpleConstant() {
    doTest();
  }

  public void testAnonymousInitializer() {
    doTest();
  }

  public void testReplaceDuplicateInsideAnonymous() {
    doTest();
  }

  public void testMakeStaticWhenUsedInInheritor() {
    doTest();
  }
}