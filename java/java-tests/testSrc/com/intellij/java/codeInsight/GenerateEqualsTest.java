// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManager;
import com.intellij.codeInsight.generation.GenerateEqualsHelper;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaDescriptorOfTestName;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;

@TestApplication
public class GenerateEqualsTest {
  private static final LightProjectDescriptor DESCRIPTOR = lightJavaTestDescriptor(LanguageLevel.JDK_1_7);
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(DESCRIPTOR);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, lightJavaDescriptorOfTestName(DESCRIPTOR), "/java/java-tests/testData");

  @Test
  public void testInstanceof() {
    doTest(Function.identity(), Function.identity(), x -> PsiField.EMPTY_ARRAY, true, false, true);
  }

  @Test
  public void testNoBaseEquals() {
    doTest(new int[0], new int[0], new int[0], false);
  }

  @Test
  public void testPrimitiveFields() {
    doTest(new int[]{0, 1, 2}, new int[0], new int[0], false);
  }

  @Test
  public void testPrimitiveFieldsJava8() {
    int[] indices = {0, 1, 2, 3, 4, 5, 6};
    doTest(indices, indices, new int[0], false);
  }

  @Test
  public void testFields() {
    doTest(new int[]{0, 1, 2}, new int[0], new int[]{1}, false);
  }

  @Test
  public void testAbstractSuperEquals() {
    doTest(new int[0], new int[0], new int[0], false);
  }

  @Test
  public void testSuperEquals() {
    doTest(new int[0], new int[0], new int[0], false);
  }

  @Test
  public void testHashCode() {
    doTest(new int[]{0, 1, 2, 3}, new int[]{0, 1, 2, 3}, new int[]{1}, false);
  }

  @Test
  public void testArrays() {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }

  @Test
  public void testOneDoubleField() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  @Test
  public void testOneDoubleFieldJava8() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  @Test
  public void testOneFloatField() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  @Test
  public void testOneField() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  @Test
  public void testNotNull() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  @Test
  public void testInsertOverride() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }
  
  @Test
  public void testLangClass() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  @Test
  public void testLocalLangClass() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  @Test
  public void testArraysClass() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  @Test
  public void testArraysFromJava15() {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }

  @Test
  public void testDifferentTypes() {
    doTest(Function.identity(), Function.identity(), fields -> PsiField.EMPTY_ARRAY, true);
  }

  @Test
  public void testDifferentTypesGetters() {
    doTest(Function.identity(), Function.identity(), fields -> PsiField.EMPTY_ARRAY, true, true, false);
  }
  
  @Test
  public void testRecordUseGettersJava21() {
    doTest(Function.identity(), Function.identity(), fields -> PsiField.EMPTY_ARRAY, true, true, true);
  }

  @Test
  public void testDifferentTypesAllNotNull() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  @Test
  public void testDifferentTypesSuperEqualsAndHashCode() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  @Test
  public void testDifferentTypesNoDouble() {
    doTest(Function.identity(), Function.identity(), Function.identity(), true);
  }

  @Test
  public void testNameConflicts() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  @Test
  public void testPrefixes() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(codeInsightFixture.get().getProject());
    settings.LOCAL_VARIABLE_NAME_PREFIX = "l_";
    settings.LOCAL_VARIABLE_NAME_SUFFIX = "_v";
    settings.PARAMETER_NAME_PREFIX = "p_";
    settings.PARAMETER_NAME_SUFFIX = "_r";
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  @Test
  public void testClassWithTypeParams() {
    doTest(Function.identity(), Function.identity(), Function.identity(), true);
  }

  @Test
  public void testDifferentTypesSuperEqualsAndHashCodeApache3() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG_3);
  }

  @Test
  public void testDifferentTypesSuperEqualsAndHashCodeGuava() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.OBJECTS_EQUAL_AND_HASH_CODE_GUAVA);
  }

  @Test
  public void testSingleArrayOfPrimitiveWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  @Test
  public void testSingleFieldWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  @Test
  public void testSingleArrayFieldWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  @Test
  public void testArrayAndNotOnlyArrayWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  @Test
  public void testArrayAndSuperWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  private void doTestWithTemplate(String templateName) {
    EqualsHashCodeTemplatesManager templatesManager = EqualsHashCodeTemplatesManager.getInstance();
    String defaultTemplateName = templatesManager.getDefaultTemplateBaseName();
    templatesManager.setDefaultTemplate(templateName);
    try {
      doTest(Function.identity(), Function.identity(), Function.identity(), true);
    }
    catch (Throwable throwable) {
      try (InputStream is = GenerateMembersUtil.class.getResourceAsStream("equalsHelper.vm")) {
        throw new RuntimeException(new String(is.readAllBytes(), StandardCharsets.UTF_8), throwable);
      }
      catch (Throwable t) {
        throwable.addSuppressed(t);
        throw throwable;
      }
    }
    finally {
      templatesManager.setDefaultTemplate(defaultTemplateName);
    }
  }

  @BeforeEach
  void setUpTemplate() {
    EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  @AfterEach
  void tearDownTemplate() {
    EqualsHashCodeTemplatesManager templatesManager = EqualsHashCodeTemplatesManager.getInstance();
    templatesManager.setDefaultTemplate(templatesManager.getInitialTemplateName());
  }

  protected void doTest(int[] equals, int[] hashCode, int[] nonNull, boolean insertOverride) {
    doTest(fields -> getIndexed(fields, equals), fields -> getIndexed(fields, hashCode), fields -> getIndexed(fields, nonNull), insertOverride);
  }

  protected void doTest(Function<PsiField[], PsiField[]> equals,
                        Function<PsiField[], PsiField[]> hashCode,
                        Function<PsiField[], PsiField[]> nonNull,
                        boolean insertOverride) {
    doTest(equals, hashCode, nonNull, insertOverride, false, false);
  }

  protected void doTest(Function<PsiField[], PsiField[]> equals,
                        Function<PsiField[], PsiField[]> hashCode,
                        Function<PsiField[], PsiField[]> nonNull,
                        boolean insertOverride,
                        boolean useAccessors,
                        boolean useInstanceofToCheckParameterType) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile("/codeInsight/generateEquals/before" + myFixture.getTestName(false) + ".java");
    myFixture.runInCommand(() -> {
      CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(myFixture.getProject());
      codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE).BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
      JavaCodeStyleSettings.getInstance(myFixture.getProject()).GENERATE_FINAL_LOCALS = true;
      JavaCodeStyleSettings.getInstance(myFixture.getProject()).INSERT_OVERRIDE_ANNOTATION = insertOverride;
      PsiElement element = myFixture.getFile().findElementAt(myFixture.getEditor().getCaretModel().getOffset());
      assert element != null;
      PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      assert aClass != null;
      PsiField[] fields = aClass.getFields();
      new GenerateEqualsHelper(myFixture.getProject(), aClass, equals.apply(fields), hashCode.apply(fields), nonNull.apply(fields),
                               useInstanceofToCheckParameterType, useAccessors).invoke();
      FileDocumentManager.getInstance().saveAllDocuments();
      myFixture.checkResultByFile("/codeInsight/generateEquals/after" + myFixture.getTestName(false) + ".java");
    });
  }

  private static PsiField[] getIndexed(PsiField[] fields, int[] indices) {
    return Arrays.stream(indices).mapToObj(index -> fields[index]).toArray(PsiField[]::new);
  }
}
