// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManager;
import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.psi.PsiField;
import com.intellij.util.Functions;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class GenerateEquals15Test extends GenerateEqualsTestCase {
  public void testArraysFromJava15() {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }

  public void testDifferentTypes() {
    doTest(Functions.id(), Functions.id(), fields -> PsiField.EMPTY_ARRAY, true
    );
  }

  public void testDifferentTypesGetters() {
    doTest(Functions.id(), Functions.id(), fields -> PsiField.EMPTY_ARRAY, true, true);
  }

  public void testDifferentTypesAllNotNull() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  public void testDifferentTypesSuperEqualsAndHashCode() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  public void testDifferentTypesNoDouble() {
    doTest(Functions.id(), Functions.id(), Functions.id(), true);
  }

  public void testNameConflicts() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
  }

  public void testClassWithTypeParams() {
    doTest(Functions.id(), Functions.id(), Functions.id(), true);
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

  public void testArrayAndNotOnlyArrayWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  public void testArrayAndSuperWithObjectsTemplate() {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  private void doTestWithTemplate(String templateName) {
    try {
      EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate(templateName);
      doTest(Functions.id(), Functions.id(), Functions.id(), true);
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

}