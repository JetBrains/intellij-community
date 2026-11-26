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
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * The override and implement check of the tests in this package.
 */
final class OverrideImplementTestUtil {
  private OverrideImplementTestUtil() {
  }

  /**
   * Configures {@code before<TestName>.java} from {@code baseDir}, overrides or implements methods in the class at the caret,
   * and checks the result against {@code after<TestName>.java}.
   * <p>
   * When {@code toImplement} is {@code null}, the class gets the first method of its first super type.
   * Otherwise, the chooser picks the methods to override ({@code false}) or to implement ({@code true}).
   * The action and the check run in one command.
   */
  static void doTest(LightJavaCodeInsightTestFixture fixture, String baseDir, boolean copyJavadoc, @Nullable Boolean toImplement) {
    String name = fixture.getTestName(false);
    fixture.configureByFile(baseDir + "before" + name + ".java");
    fixture.runInCommand(() -> {
      int offset = fixture.getEditor().getCaretModel().getOffset();
      PsiElement context = fixture.getFile().findElementAt(offset);
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
          OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(fixture.getEditor(), psiClass, candidates, copyJavadoc, true);
        }
        else {
          OverrideImplementUtil.chooseAndOverrideOrImplementMethods(fixture.getProject(), fixture.getEditor(), psiClass, toImplement);
        }
      });
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      fixture.checkResultByFile(baseDir + "after" + name + ".java");
    });
  }
}
