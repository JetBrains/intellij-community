/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightProjectFixture;

@TestApplication
public class OverrideImplementFromLibraryTest {
  private final TestFixture<Project> projectFixture = codeInsightProjectFixture();
  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightFixture(projectFixture);

  @BeforeEach
  void setUpTestDataPath() {
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath());
  }

  private static final String BASE_DIR = "/codeInsight/overrideImplement/libraries/";
  @Test
  public void testSubstituteTypeParameterBounds() {
    doTest("IDEA162079", false);
  }

  private void doTest(String name, @Nullable Boolean toImplement) {
    CodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiTestUtil.addLibrary(myFixture.getModule(), JavaTestUtil.getJavaTestDataPath() + BASE_DIR + name + ".jar");
    myFixture.configureByFile(BASE_DIR + "before" + name + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      PsiElement context = myFixture.getFile().findElementAt(offset);
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
          OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(myFixture.getEditor(), psiClass, candidates, false, true);
        }
        else {
          OverrideImplementUtil.chooseAndOverrideOrImplementMethods(myFixture.getProject(), myFixture.getEditor(), psiClass, toImplement);
        }
      });
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    });
    myFixture.checkResultByFile(BASE_DIR + "after" + name + ".java");
  }
}
