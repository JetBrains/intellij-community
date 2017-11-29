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

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManager;
import com.intellij.psi.PsiField;
import com.intellij.util.Functions;

/**
 * @author dsl
 */
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
    finally {
      EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
    }
  }

}