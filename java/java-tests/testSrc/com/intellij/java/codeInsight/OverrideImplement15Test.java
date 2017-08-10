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
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.generation.JavaOverrideMethodsHandler;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.impl.ImplementAbstractMethodHandler;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.MapDataContext;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author ven
 */
public class OverrideImplement15Test extends LightCodeInsightTestCase {
  private static final String BASE_DIR = "/codeInsight/overrideImplement/";

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_5;
  }

  public void testSimple() { doTest(true); }
  public void testAnnotation() { doTest(true); }
  public void testJavadocForChangedParamName() { doTest(true); }
  public void testThrowsListFromMethodHierarchy() { doTest(true); }
  public void testThrowsListUnrelatedMethods() { doTest(true); }
  public void testIncomplete() { doTest(false); }
  public void testSubstitutionInTypeParametersList() { doTest(false); }
  public void testTestMissed() { doTest(false); }
  public void testWildcard() { doTest(false); }
  public void testTypeParam() { doTest(false); }
  public void testInterfaceAndAbstractClass() { doTest(false); }
  public void testRawSuper() { doTest(false); }
  public void testSubstituteBoundInMethodTypeParam() { doTest(false); }
  public void testClone() { doTest(false); }
  public void testOnTheLineWithExistingExpression() { doTest(false); }
  public void testSimplifyObjectWildcard() { doTest(false); }
  public void testErasureWildcard() { doTest(false); }
  public void testMultipleInterfaceInheritance() { doTest(false); }
  public void testResolveTypeParamConflict() { doTest(false); }
  public void testRawInheritance() { doTest(false); }
  public void testRawInheritanceWithMethodTypeParameters() { doTest(false); }

  public void testLongFinalParameterList() {
    CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    try {
      CommonCodeStyleSettings javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);
      javaSettings.RIGHT_MARGIN = 80;
      javaSettings.KEEP_LINE_BREAKS = true;
      codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS = true;
      javaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
      CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(codeStyleSettings);
      doTest(false);
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
  }

  public void testOverridingLibraryFunctionWithConfiguredParameterPrefix() {
    CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    try {
      codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).PARAMETER_NAME_PREFIX = "in";
      CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(codeStyleSettings);
      doTest(false);
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
  }

  public void testLongParameterList() {
    CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    try {
      CommonCodeStyleSettings javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);
      javaSettings.RIGHT_MARGIN = 80;
      javaSettings.KEEP_LINE_BREAKS = false;
      codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS = false;
      javaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
      CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(codeStyleSettings);
      doTest(false);
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
  }

  public void testImplementedConstructorsExcluded() {
    configureByFile(BASE_DIR + getTestName(false) + ".java");
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement context = getFile().findElementAt(offset);
    PsiClass psiClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    assert psiClass != null;

    final Collection<MethodSignature> signatures = OverrideImplementExploreUtil.getMethodSignaturesToOverride(psiClass);
    final Collection<String> strings = ContainerUtil.map(signatures, FunctionUtil.string());

    assertTrue(strings.toString(), strings.contains("HierarchicalMethodSignatureImpl: A([PsiType:String])"));
    assertFalse(strings.toString(), strings.contains("HierarchicalMethodSignatureImpl: A([])"));
  }

  public void testEnumConstant() {
    String name = getTestName(false);
    configureByFile(BASE_DIR + "before" + name + ".java");
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement context = getFile().findElementAt(offset);
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(context, PsiMethod.class);
    assert psiMethod != null;
    final PsiClass aClass = psiMethod.getContainingClass();
    assert aClass != null && aClass.isEnum();
    final PsiField[] fields = aClass.getFields();
    new ImplementAbstractMethodHandler(getProject(), getEditor(), psiMethod).implementInClass(fields);
    checkResultByFile(BASE_DIR + "after" + name + ".java");
  }

  public void testInAnnotationType() {
    String name = getTestName(false);
    configureByFile(BASE_DIR + "before" + name + ".java");
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement context = getFile().findElementAt(offset);
    final PsiClass aClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    assertTrue(aClass != null && aClass.isAnnotationType());
    final JavaOverrideMethodsHandler handler = new JavaOverrideMethodsHandler();
    assertTrue(handler.isValidFor(getEditor(), getFile()));
    assertFalse(handler.isAvailableForQuickList(getEditor(), getFile(), new MapDataContext()));
  }

  private void doTest(boolean copyJavadoc) { doTest(copyJavadoc, null); }

  private void doTest(boolean copyJavadoc, @Nullable Boolean toImplement) {
    String name = getTestName(false);
    configureByFile(BASE_DIR + "before" + name + ".java");
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
        OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(getEditor(), psiClass, candidates, copyJavadoc, true);
      }
      else {
        OverrideImplementUtil.chooseAndOverrideOrImplementMethods(getProject(), getEditor(), psiClass, toImplement);
      }
    });

    checkResultByFile(BASE_DIR + "after" + name + ".java");
  }
}