package com.intellij.codeInsight;

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

}