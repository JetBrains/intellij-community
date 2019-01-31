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
package com.intellij.java.codeInsight.intention;

import com.intellij.codeInsight.intention.impl.CreateSubclassAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.refactoring.LightMultiFileTestCase;
import com.intellij.util.ui.UIUtil;

/**
 * @author yole
 */
public class CreateSubclassTest extends LightMultiFileTestCase {
  public void testGenerics() {
    doTest();
  }

  public void testImports() {
    doTest();
  }

  public void testInnerClassImplement() {
    doTestInner();
  }

  public void testInnerClass() {
    doTestInner();
  }

  private void doTestInner() {
    doTest(() -> {
      PsiClass superClass = myFixture.findClass("Test");
      final PsiClass inner = superClass.findInnerClassByName("Inner", false);
      assertNotNull(inner);
      CreateSubclassAction.createInnerClass(inner);
      UIUtil.dispatchAllInvocationEvents();
    });
  }

  private void doTest() {
    doTest(() -> {
      PsiDirectory root = getPsiManager().findDirectory(myFixture.getTempDirFixture().findOrCreateDir(""));
      PsiClass superClass = myFixture.findClass("Superclass");
      ApplicationManager.getApplication().invokeLater(
        () -> CreateSubclassAction.createSubclass(superClass, root, "Subclass"));
      UIUtil.dispatchAllInvocationEvents();
    });
  }

  @Override
  protected String getTestDataPath() {
    return PathManagerEx.getTestDataPath() + "/codeInsight/createSubclass/";
  }
}
