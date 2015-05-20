package com.intellij.codeInsight;

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManager;
import com.intellij.psi.PsiField;
import com.intellij.util.Function;

/**
 * @author dsl
 */
public class GenerateEquals15Test extends GenerateEqualsTestCase {
  public void testArraysFromJava15() throws Exception {
    doTest(new int[]{0, 1, 2}, new int[]{0, 1, 2}, new int[0], false);
  }

  public void testDifferentTypes() throws Exception {
    doTest(Function.ID, Function.ID, new Function<PsiField[], PsiField[]>() {
             @Override
             public PsiField[] fun(PsiField[] fields) {
               return new PsiField[0];
             }
           }, true
    );
  }

  public void testDifferentTypesGetters() throws Exception {
    doTest(Function.ID, Function.ID, new Function<PsiField[], PsiField[]>() {
      @Override
      public PsiField[] fun(PsiField[] fields) {
        return new PsiField[0];
      }
    }, true, true);
  }

  public void testDifferentTypesAllNotNull() throws Exception {
    doTest(Function.ID, Function.ID, Function.ID, true);
  }

  public void testDifferentTypesSuperEqualsAndHashCode() throws Exception {
    doTest(Function.ID, Function.ID, Function.ID, true);
  }

  public void testDifferentTypesNoDouble() throws Exception {
    doTest(Function.ID, Function.ID, Function.ID, true);
  }

  public void testNameConflicts() throws Exception {
    doTest(Function.ID, Function.ID, Function.ID, true);
  }

  public void testClassWithTypeParams() throws Exception {
    doTest(Function.ID, Function.ID, Function.ID, true);
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
      doTest(Function.ID, Function.ID, Function.ID, true);
    }
    finally {
      EqualsHashCodeTemplatesManager.getInstance().setDefaultTemplate(EqualsHashCodeTemplatesManager.INTELLI_J_DEFAULT);
    }
  }

}