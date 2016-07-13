/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.codeInsight;

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManager;
import com.intellij.psi.PsiField;
import com.intellij.util.Functions;

/**
 * @author dsl
 */
public class GenerateEquals15Test extends GenerateEqualsTestCase {
  public void testArraysFromJava15() throws Exception {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }

  public void testDifferentTypes() throws Exception {
    doTest(Functions.id(), Functions.id(), fields -> PsiField.EMPTY_ARRAY, true
    );
  }

  public void testDifferentTypesGetters() throws Exception {
    doTest(Functions.id(), Functions.id(), fields -> PsiField.EMPTY_ARRAY, true, true);
  }

  public void testDifferentTypesAllNotNull() throws Exception {
    doTest(Functions.id(), Functions.id(), Functions.id(), true);
  }

  public void testDifferentTypesSuperEqualsAndHashCode() throws Exception {
    doTest(Functions.id(), Functions.id(), Functions.id(), true);
  }

  public void testDifferentTypesNoDouble() throws Exception {
    doTest(Functions.id(), Functions.id(), Functions.id(), true);
  }

  public void testNameConflicts() throws Exception {
    doTest(Functions.id(), Functions.id(), Functions.id(), true);
  }

  public void testClassWithTypeParams() throws Exception {
    doTest(Functions.id(), Functions.id(), Functions.id(), true);
  }

  public void testDifferentTypesSuperEqualsAndHashCodeApache3() throws Exception {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.EQUALS_HASH_CODE_BUILDER_APACHE_COMMONS_LANG_3);
  }

  public void testDifferentTypesSuperEqualsAndHashCodeGuava() throws Exception {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.OBJECTS_EQUAL_AND_HASH_CODE_GUAVA);
  }

  public void testSingleArrayOfPrimitiveWithObjectsTemplate() throws Exception {
    doTestWithTemplate(EqualsHashCodeTemplatesManager.JAVA_UTIL_OBJECTS_EQUALS_AND_HASH_CODE);
  }

  private void doTestWithTemplate(String templateName) throws Exception {
    try {
      EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate(templateName);
      doTest(Functions.id(), Functions.id(), Functions.id(), true);
    }
    finally {
      EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
    }
  }

}