// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.testFramework.LightJavaCodeInsightTestCase;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.Function;

public class GenerateEqualsTest extends LightJavaCodeInsightTestCase {

  public void testInstanceof() {
    doTest(Function.identity(), Function.identity(), x -> PsiField.EMPTY_ARRAY, true, false, true);
  }

  public void testNoBaseEquals() {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testPrimitiveFields() {
    doTest(new int[]{0, 1, 2}, new int[0], new int[0], false);
  }

  public void testPrimitiveFieldsJava8() {
    int[] indices = {0, 1, 2, 3, 4, 5, 6};
    doTest(indices, indices, new int[0], false);
  }

  public void testFields() {
    doTest(new int[]{0, 1, 2}, new int[0], new int[]{1}, false);
  }

  public void testAbstractSuperEquals() {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testSuperEquals() {
    doTest(new int[0], new int[0], new int[0], false);
  }

  public void testHashCode() {
    doTest(new int[]{0, 1, 2, 3}, new int[]{0, 1, 2, 3}, new int[]{1}, false);
  }

  public void testArrays() {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }

  public void testOneDoubleField() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testOneDoubleFieldJava8() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testOneFloatField() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testOneField() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testNotNull() {
    doTest(new int[]{0}, new int[]{0}, new int[0], false);
  }

  public void testInsertOverride() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }
  
  public void testLangClass() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  public void testLocalLangClass() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  public void testArraysClass() {
    doTest(new int[]{0}, new int[]{0}, new int[0], true);
  }

  public void testArraysFromJava15() {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }

  public void testDifferentTypes() {
    doTest(Function.identity(), Function.identity(), fields -> PsiField.EMPTY_ARRAY, true);
  }

  public void testDifferentTypesGetters() {
    doTest(Function.identity(), Function.identity(), fields -> PsiField.EMPTY_ARRAY, true, true, false);
  }
  
  public void testRecordUseGettersJava21() {
    doTest(Function.identity(), Function.identity(), fields -> PsiField.EMPTY_ARRAY, true, true, true);
  }

  public void testDifferentTypesAllNotNull() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  public void testDifferentTypesSuperEqualsAndHashCode() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  public void testDifferentTypesNoDouble() {
    doTest(Function.identity(), Function.identity(), Function.identity(), true);
  }

  public void testNameConflicts() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  public void testPrefixes() {
    JavaCodeStyleSettings settings = JavaCodeStyleSettings.getInstance(getProject());
    settings.LOCAL_VARIABLE_NAME_PREFIX = "l_";
    settings.LOCAL_VARIABLE_NAME_SUFFIX = "_v";
    settings.PARAMETER_NAME_PREFIX = "p_";
    settings.PARAMETER_NAME_SUFFIX = "_r";
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  public void testClassWithTypeParams() {
    doTest(Function.identity(), Function.identity(), Function.identity(), true);
  }

  public void testDifferentTypesSuperEqualsAndHashCodeApache3() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG_3);
  }

  public void testDifferentTypesSuperEqualsAndHashCodeGuava() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.OBJECTS_EQUAL_AND_HASH_CODE_GUAVA);
  }

  public void testSingleArrayOfPrimitiveWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  public void testSingleFieldWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  public void testSingleArrayFieldWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  public void testArrayAndNotOnlyArrayWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  public void testArrayAndSuperWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  private void doTestWithTemplate(String templateName) {
    try {
      EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate(templateName);
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
      EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  protected void doTest(final int[] equals,
                        final int[] hashCode,
                        final int[] nonNull,
                        boolean insertOverride) {
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
    configureByFile("/codeInsight/generateEquals/before" + getTestName(false) + ".java");
    CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(getProject());
    codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE).BINARY_OPERATION_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
    JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_LOCALS = true;
    JavaCodeStyleSettings.getInstance(getProject()).INSERT_OVERRIDE_ANNOTATION = insertOverride;
    PsiElement element = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    assert element != null;
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    assert aClass != null;
    PsiField[] fields = aClass.getFields();
    new GenerateEqualsHelper(getProject(), aClass, equals.apply(fields), hashCode.apply(fields), nonNull.apply(fields),
                             useInstanceofToCheckParameterType, useAccessors).invoke();
    FileDocumentManager.getInstance().saveAllDocuments();
    checkResultByFile("/codeInsight/generateEquals/after" + getTestName(false) + ".java");
  }

  private static PsiField[] getIndexed(PsiField[] fields, int[] indices) {
    return Arrays.stream(indices).mapToObj(index -> fields[index]).toArray(PsiField[]::new);
  }

  @Override
  protected LanguageLevel getDefaultLanguageLevel() {
    return LanguageLevel.JDK_1_7;
  }
}
