/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightFixtureTestCase;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class OverrideImplementFromLibraryTest extends CodeInsightFixtureTestCase {

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath();
  }

  private static final String BASE_DIR = "/codeInsight/overrideImplement/libraries/";
  public void testSubstituteTypeParameterBounds() {
    doTest("IDEA162079", false);
  }

  private void doTest(String name, @Nullable Boolean toImplement) {
    PsiTestUtil.addLibrary(myModule, JavaTestUtil.getJavaTestDataPath() + BASE_DIR + name + ".jar");
    myFixture.configureByFile(BASE_DIR + "before" + name + ".java");
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
        final List<PsiMethodMember> candidates = Collections.singletonList(new PsiMethodMember(method,
                                                                                               OverrideImplementExploreUtil
                                                                                                 .correctSubstitutor(method,
                                                                                                                     substitutor)));
        OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(getEditor(), psiClass, candidates, false, true);
      }
      else {
        OverrideImplementUtil.chooseAndOverrideOrImplementMethods(getProject(), getEditor(), psiClass, toImplement);
      }
    });

    myFixture.checkResultByFile(BASE_DIR + "after" + name + ".java");
  }
}
