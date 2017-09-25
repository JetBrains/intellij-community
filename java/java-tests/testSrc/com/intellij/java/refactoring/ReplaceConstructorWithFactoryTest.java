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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.replaceConstructorWithFactory.ReplaceConstructorWithFactoryProcessor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ReplaceConstructorWithFactoryTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testEmptyConstructor() { runTest("01", null); }

  public void testSubclass() { runTest("02", null); }

  public void testDefaultConstructor() { runTest("03", null); }
  public void testDefaultConstructorWithTypeParams() { runTest("TypeParams", null); }

  public void testInnerClass() { runTest("04", "OuterClass"); }

  public void testSubclassVisibility() { runTest("05", null); }

  public void testImplicitConstructorUsages() { runTest("06", null); }

  public void testImplicitConstructorCreation() { runTest("07", null); }

  public void testConstructorTypeParameters() { runTest("08", null); }

  private void runTest(final String testIndex, @NonNls String targetClassName) {
    configureByFile("/refactoring/replaceConstructorWithFactory/before" + testIndex + ".java");
    perform(targetClassName);
    checkResultByFile("/refactoring/replaceConstructorWithFactory/after" + testIndex + ".java");
  }


  private void perform(String targetClassName) {
    int offset = myEditor.getCaretModel().getOffset();
    PsiElement element = myFile.findElementAt(offset);
    PsiMethod constructor = null;
    PsiClass aClass = null;
    while (true) {
      if (element == null || element instanceof PsiFile) {
        assertTrue(false);
        return;
      }

      if (element instanceof PsiMethod && ((PsiMethod)element).isConstructor()) {
        constructor = (PsiMethod)element;
        break;
      }

      if (element instanceof PsiClass && ((PsiClass)element).getConstructors().length == 0) {
        aClass = (PsiClass)element;
        break;
      }
      element = element.getParent();
    }
    PsiClass targetClass = null;
    if (targetClassName != null) {
      targetClass = JavaPsiFacade.getInstance(getProject()).findClass(targetClassName, GlobalSearchScope.allScope(getProject()));
      assertTrue(targetClass != null);
    }

    final ReplaceConstructorWithFactoryProcessor replaceConstructorWithFactoryProcessor;
    if (constructor != null) {
      if (targetClass == null) {
        targetClass = constructor.getContainingClass();
      }
      replaceConstructorWithFactoryProcessor = new ReplaceConstructorWithFactoryProcessor(
        getProject(), constructor, constructor.getContainingClass(), targetClass, "new" + constructor.getName());
    }
    else {
      if (targetClass == null) {
        targetClass = aClass;
      }
      replaceConstructorWithFactoryProcessor = new ReplaceConstructorWithFactoryProcessor(
        getProject(), null, aClass, targetClass, "new" + aClass.getName());
    }
    replaceConstructorWithFactoryProcessor.run();
  }
}
