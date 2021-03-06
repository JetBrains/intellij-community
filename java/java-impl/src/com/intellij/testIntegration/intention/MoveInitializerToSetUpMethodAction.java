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
package com.intellij.testIntegration.intention;

import com.intellij.codeInsight.TestFrameworks;
import com.intellij.codeInsight.intention.impl.BaseMoveInitializerToMethodAction;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testIntegration.JavaTestFramework;
import com.intellij.testIntegration.TestFramework;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

public class MoveInitializerToSetUpMethodAction extends BaseMoveInitializerToMethodAction {
  private static final Logger LOG = Logger.getInstance(MoveInitializerToSetUpMethodAction.class);

  @Override
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @Override
  @NotNull
  public String getText() {
    return JavaBundle.message("intention.move.initializer.to.set.up");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final boolean isAvailable = super.isAvailable(project, editor, element);
    if (isAvailable) {
      final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
      LOG.assertTrue(field != null);
      final PsiClass aClass = field.getContainingClass();
      LOG.assertTrue(aClass != null);
      TestFramework testFramework = TestFrameworks.detectFramework(aClass);
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
      if (testFramework instanceof JavaTestFramework) {
        try {
          ((JavaTestFramework)testFramework).createSetUpPatternMethod(elementFactory);
          return testFramework.isTestClass(aClass) || testFramework.findSetUpMethod(aClass) instanceof PsiMethod;
        }
        catch (Exception e) {
          return false;
        }
      }
    }
    return false;
  }

  @NotNull
  @Override
  protected Collection<String> getUnsuitableModifiers() {
    return Arrays.asList(PsiModifier.STATIC, PsiModifier.FINAL);
  }

  @NotNull
  @Override
  protected Collection<PsiMethod> getOrCreateMethods(@NotNull Project project, @NotNull Editor editor, PsiFile file, @NotNull PsiClass aClass) {
    TestFramework testFramework = TestFrameworks.detectFramework(aClass);
    PsiElement setUpMethod = null;
    if (testFramework != null) {
      setUpMethod = testFramework.findSetUpMethod(aClass);
      if (setUpMethod == null) {
        setUpMethod = testFramework.findOrCreateSetUpMethod(aClass);
      }
    }
    return setUpMethod instanceof PsiMethod ? Collections.singletonList((PsiMethod)setUpMethod) : Collections.emptyList();
  }
}
