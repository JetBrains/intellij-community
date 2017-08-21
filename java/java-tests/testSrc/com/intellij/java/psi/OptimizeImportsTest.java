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
package com.intellij.java.psi;

import com.intellij.openapi.application.ex.PathManagerEx;

public class OptimizeImportsTest extends OptimizeImportsTestCase {
  private static final String BASE_PATH = PathManagerEx.getTestDataPath() + "/psi/optimizeImports";

  @Override
  protected String getTestDataPath() {
    return BASE_PATH;
  }

  public void testSCR6138() { doTest(); }
  public void testSCR18364() { doTest(); }
  public void testStaticImports1() { doTest(); }
  public void testStaticImportsToOptimize() { doTest(); }
  public void testStaticImportsToOptimizeMixed() { doTest(); }
  public void testStaticImportsToOptimize2() { doTest(); }
  public void testEmptyImportList() { doTest(); }
  public void testIDEADEV10716() { doTest(); }
  public void testUnresolvedImports() { doTest(); }
  public void testUnresolvedImports2() { doTest(); }
  public void testNewImportListIsEmptyAndCommentPreserved() { doTest(); }
  public void testNewImportListIsEmptyAndJavaDocWithInvalidCodePreserved() { doTest(); }

  public void testDontCollapseToOnDemandImport() { doTest(); }
  public void testIgnoreInaccessible() { doTest();}

  public void testEnsureConflictingImportsNotCollapsed() {
    doTest();
  }

  public void testSameNamedImportedClasses() {
    doTest();
  }

  public void testConflictingWithJavaLang() {
    doTest();
  }

  public void testExcludeNonStaticElementsFromStaticConflictingMembers() {
    doTest();
  }

  private void doTest() {
    doTest(".java");
  }
}
