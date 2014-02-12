/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 20-Aug-2008
 */
package com.intellij.refactoring;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.wrapreturnvalue.WrapReturnValueProcessor;
import com.intellij.JavaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class WrapReturnValueTest extends MultiFileTestCase{
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  @NotNull
  @Override
  protected String getTestRoot() {
    return "/refactoring/wrapReturnValue/";
  }

  private void doTest(final boolean existing) throws Exception {
    doTest(existing, null);
  }

  private void doTest(final boolean existing, @NonNls String exceptionMessage) throws Exception {
    doTest(existing, exceptionMessage, false);
  }

  private void doTest(final boolean existing, String exceptionMessage, final boolean createInnerClass) throws Exception {
    try {
      doTest(new PerformAction() {
        @Override
        public void performAction(final VirtualFile rootDir, final VirtualFile rootAfter) throws Exception {
          PsiClass aClass = myJavaFacade.findClass("Test", GlobalSearchScope.projectScope(getProject()));

          assertNotNull("Class Test not found", aClass);

          final PsiMethod method = aClass.findMethodsByName("foo", false)[0];



          @NonNls final String wrapperClassName = "Wrapper";

          final PsiClass wrapperClass = myJavaFacade.findClass(wrapperClassName, GlobalSearchScope.projectScope(getProject()));

          assertTrue(!existing || wrapperClass != null);
          final PsiField delegateField = existing ? wrapperClass.findFieldByName("myField", false) : null;
          WrapReturnValueProcessor processor = new WrapReturnValueProcessor(wrapperClassName, "",
                                                                            null, method, existing, createInnerClass,
                                                                            delegateField);
          processor.run();
          /*LocalFileSystem.getInstance().refresh(false);
          FileDocumentManager.getInstance().saveAllDocuments();*/
        }
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

  public void testSimple() throws Exception {
    doTest(false);
  }

  public void testGenerics() throws Exception {
    doTest(false);
  }

  public void testInconsistentWrapper() throws Exception {
    doTest(true, "Existing class does not have getter for selected field");
  }

  public void testWrapper() throws Exception {
    doTest(true);
  }

  public void testStrip() throws Exception {
    doTest(true);
  }

  public void testNoConstructor() throws Exception {
    doTest(true, "Existing class does not have appropriate constructor");
  }

  public void testInnerClass() throws Exception {
    doTest(false, null, true);
  }

  public void testHierarchy() throws Exception {
    doTest(false, null, true);
  }

  public void testAnonymous() throws Exception {
    doTest(true, null, false);
  }

  public void testWrongFieldAssignment() throws Exception {
    doTest(true, "Existing class does not have appropriate constructor", false);
  }

  public void testWrongFieldType() throws Exception {
    doTest(true, "Existing class does not have appropriate constructor", false);
  }

  public void testStaticMethodInnerClass() throws Exception {
    doTest(false, null, true);
  }

  public void testRawReturnType() throws Exception {
    doTest(true, "Existing class does not have appropriate constructor");
  }

  public void testReturnInsideLambda() throws Exception {
    doTest(false, null, true);
  }
  
}