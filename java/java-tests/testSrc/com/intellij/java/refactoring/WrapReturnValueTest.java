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

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.MultiFileTestCase;
import com.intellij.refactoring.wrapreturnvalue.WrapReturnValueProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author anna
 */
public class WrapReturnValueTest extends MultiFileTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/wrapReturnValue/";
  }

  public void testSimple() { doTest(false); }
  public void testGenerics() { doTest(false); }
  public void testInconsistentWrapper() { doTest(true, "Existing class does not have getter for selected field"); }
  public void testWrapper() { doTest(true); }
  public void testStrip() { doTest(true); }
  public void testNoConstructor() { doTest(true, "Existing class does not have appropriate constructor"); }
  public void testInnerClass() { doTest(false, null, true); }
  public void testHierarchy() { doTest(false, null, true); }
  public void testAnonymous() { doTest(true, null, false); }
  public void testWrongFieldAssignment() { doTest(true, "Existing class does not have appropriate constructor", false); }
  public void testInferFieldType() { doTest(true, null, false); }
  public void testInferFieldTypeArg() { doTest(true, null, false); }
  public void testWrongFieldType() { doTest(true, "Existing class does not have appropriate constructor", false); }
  public void testStaticMethodInnerClass() { doTest(false, null, true); }
  public void testOpenMethodReference() { doTest(false, null, true); }
  public void testRawReturnType() { doTest(true, "Existing class does not have appropriate constructor"); }
  public void testReturnInsideLambda() { doTest(false, null, true); }
  public void testTypeAnnotations() { doTest(false); }
  public void testWithLambdaInside() { doTest(true); }

  private void doTest(final boolean existing) {
    doTest(existing, null);
  }

  private void doTest(final boolean existing, @NonNls String exceptionMessage) {
    doTest(existing, exceptionMessage, false);
  }

  private void doTest(final boolean existing, String exceptionMessage, final boolean createInnerClass) {
    try {
      doTest((rootDir, rootAfter) -> {
        PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(getProject()));
        assertNotNull("Class Test not found", aClass);
        PsiMethod method = aClass.findMethodsByName("foo", false)[0];
        String wrapperClassName = "Wrapper";
        PsiClass wrapperClass = myJavaFacade.findClass(wrapperClassName, GlobalSearchScope.projectScope(getProject()));
        assertTrue(!existing || wrapperClass != null);
        PsiField delegateField = existing ? wrapperClass.findFieldByName("myField", false) : null;
        new WrapReturnValueProcessor(wrapperClassName, "", null, method, existing, createInnerClass, delegateField).run();
      });
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      if (exceptionMessage != null) {
        assertEquals(exceptionMessage, e.getMessage());
        return;
      }
      throw e;
    }
    if (exceptionMessage != null) {
      fail("Conflict was not found");
    }
  }
}