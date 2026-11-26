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

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.generation.JavaOverrideMethodsHandler;
import com.intellij.codeInsight.generation.OverrideImplementExploreUtil;
import com.intellij.codeInsight.intention.impl.ImplementAbstractMethodHandler;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ReadAction;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.MapDataContext;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtil;
import com.intellij.util.FunctionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;

import static com.intellij.java.codeInsight.OverrideImplementTestUtil.doTest;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class OverrideImplement15Test {
  private static final String BASE_DIR = "/codeInsight/overrideImplement/";
  private static final TestFixture<LightJavaProject> projectFixture =
    lightJavaProjectFixture(lightJavaTestDescriptor(LanguageLevel.JDK_1_5));
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  @Test
  public void testSimple() { doTest(codeInsightFixture.get(), BASE_DIR, true, null); }
  @Test
  public void testAnnotation() { doTest(codeInsightFixture.get(), BASE_DIR, true, null); }
  @Test
  public void testTransformJBAnnotations() {
    doCustomNotNullAnnotations();
  }

  @Test
  public void testDoNotTransformJBAnnotationsWhenNewNonAvailable() {
    doCustomNotNullAnnotations();
  }
  
  private void doCustomNotNullAnnotations() {
    NullableNotNullManager nullableNotNullManager = NullableNotNullManager.getInstance(codeInsightFixture.get().getProject());
    List<String> notNulls = nullableNotNullManager.getNotNulls();
    List<String> nullables = nullableNotNullManager.getNullables();

    try {
      nullableNotNullManager.setNotNulls(ArrayUtil.prepend("p.NN",ArrayUtil.toStringArray(notNulls)));
      nullableNotNullManager.setNullables(ArrayUtil.prepend("p.N",ArrayUtil.toStringArray(nullables)));
      doTest(codeInsightFixture.get(), BASE_DIR, true, true);
    }
    finally {
      nullableNotNullManager.setNotNulls(ArrayUtil.toStringArray(notNulls));
      nullableNotNullManager.setNullables(ArrayUtil.toStringArray(nullables));
    }
  }

  @Test
  public void testJavadocForChangedParamName() { doTest(codeInsightFixture.get(), BASE_DIR, true, null); }
  @Test
  public void testThrowsListFromMethodHierarchy() { doTest(codeInsightFixture.get(), BASE_DIR, true, null); }
  @Test
  public void testThrowsListUnrelatedMethods() { doTest(codeInsightFixture.get(), BASE_DIR, true, null); }
  @Test
  public void testIncomplete() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testSubstitutionInTypeParametersList() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testTestMissed() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testWildcard() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testTypeParam() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testInterfaceAndAbstractClass() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testRawSuper() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testSubstituteBoundInMethodTypeParam() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testClone() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testOnTheLineWithExistingExpression() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testSimplifyObjectWildcard() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testErasureWildcard() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testMultipleInterfaceInheritance() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testResolveTypeParamConflict() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testRawInheritance() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testRawInheritanceWithMethodTypeParameters() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }
  @Test
  public void testVoidNameSuggestion() { doTest(codeInsightFixture.get(), BASE_DIR, false, null); }

  @Test
  public void testLongFinalParameterList() {
    CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    CommonCodeStyleSettings javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.RIGHT_MARGIN = 80;
    javaSettings.KEEP_LINE_BREAKS = true;
    JavaCodeStyleSettings.getInstance(codeInsightFixture.get().getProject()).GENERATE_FINAL_PARAMETERS = true;
    javaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    doTest(codeInsightFixture.get(), BASE_DIR, false, null);
  }

  @Test
  public void testOverridingLibraryFunctionWithConfiguredParameterPrefix() {
    JavaCodeStyleSettings.getInstance(codeInsightFixture.get().getProject()).PARAMETER_NAME_PREFIX = "in";
    doTest(codeInsightFixture.get(), BASE_DIR, false, null);
  }

  @Test
  public void testLongParameterList() {
    CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(codeInsightFixture.get().getProject());
    CommonCodeStyleSettings javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);
    javaSettings.RIGHT_MARGIN = 80;
    javaSettings.KEEP_LINE_BREAKS = false;
    JavaCodeStyleSettings.getInstance(codeInsightFixture.get().getProject()).GENERATE_FINAL_PARAMETERS = false;
    javaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    doTest(codeInsightFixture.get(), BASE_DIR, false, null);
  }

  @Test
  public void testImplementedConstructorsExcluded() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(BASE_DIR + myFixture.getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      PsiElement context = myFixture.getFile().findElementAt(offset);
      PsiClass psiClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
      assert psiClass != null;

      final Collection<MethodSignature> signatures = OverrideImplementExploreUtil.getMethodSignaturesToOverride(psiClass);
      final Collection<String> strings = ContainerUtil.map(signatures, FunctionUtil.string());

      assertTrue(strings.contains("HierarchicalMethodSignatureImpl: A([PsiType:String])"), strings.toString());
      assertFalse(strings.contains("HierarchicalMethodSignatureImpl: A([])"), strings.toString());
    });
  }

  @Test
  public void testEnumConstant() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile(BASE_DIR + "before" + name + ".java");
    myFixture.runInCommand(() -> {
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      PsiElement context = myFixture.getFile().findElementAt(offset);
      PsiMethod psiMethod = PsiTreeUtil.getParentOfType(context, PsiMethod.class);
      assert psiMethod != null;
      final PsiClass aClass = psiMethod.getContainingClass();
      assert aClass != null && aClass.isEnum();
      final PsiField[] fields = aClass.getFields();
      new ImplementAbstractMethodHandler(myFixture.getProject(), myFixture.getEditor(), psiMethod).implementInClass(fields);
      myFixture.checkResultByFile(BASE_DIR + "after" + name + ".java");
    });
  }

  @Test
  public void testInAnnotationType() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    String name = myFixture.getTestName(false);
    myFixture.configureByFile(BASE_DIR + "before" + name + ".java");
    myFixture.runInCommand(() -> {
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      PsiElement context = myFixture.getFile().findElementAt(offset);
      final PsiClass aClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
      assertTrue(aClass != null && aClass.isAnnotationType());
      final JavaOverrideMethodsHandler handler = new JavaOverrideMethodsHandler();
      assertTrue(handler.isValidFor(myFixture.getEditor(), myFixture.getFile()));
      assertFalse(handler.isAvailableForQuickList(myFixture.getEditor(), myFixture.getFile(), new MapDataContext()));
    });
  }
}