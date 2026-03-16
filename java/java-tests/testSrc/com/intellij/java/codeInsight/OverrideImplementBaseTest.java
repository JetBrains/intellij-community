// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

abstract class OverrideImplementBaseTest extends LightJavaCodeInsightTestCase {
  protected abstract String getBaseDir();

  protected void doTest(boolean copyJavadoc) { doTest(copyJavadoc, null); }

  protected void doTest(boolean copyJavadoc, @Nullable Boolean toImplement) {
    String name = getTestName(false);
    configureByFile(getBaseDir() + "before" + name + ".java");
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement context = getFile().findElementAt(offset);
    PsiClass psiClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    assert psiClass != null;
    ApplicationManager.getApplication().runWriteAction(() -> {
      if (toImplement == null) {
        PsiClassType[] implement = psiClass.getImplementsListTypes();
        final PsiClass superClass = implement.length == 0 ? psiClass.getSuperClass() : implement[0].resolve();
        assert superClass != null;
        PsiMethod method = superClass.getMethods()[0];
        final PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, psiClass, PsiSubstitutor.EMPTY);
        final List<PsiMethodMember> candidates = Collections.singletonList(
          new PsiMethodMember(method, OverrideImplementExploreUtil.correctSubstitutor(method, substitutor)));
        OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(getEditor(), psiClass, candidates, copyJavadoc, true);
      }
      else {
        OverrideImplementUtil.chooseAndOverrideOrImplementMethods(getProject(), getEditor(), psiClass, toImplement);
      }
    });
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    checkResultByFile(getBaseDir() + "after" + name + ".java");
  }
}
