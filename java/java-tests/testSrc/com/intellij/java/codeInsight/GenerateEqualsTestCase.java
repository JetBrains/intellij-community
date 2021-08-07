// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.generation.EqualsHashCodeTemplatesManager;
import com.intellij.codeInsight.generation.GenerateEqualsHelper;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.Function;

import java.util.ArrayList;


public abstract class GenerateEqualsTestCase extends LightJavaCodeInsightTestCase {
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

  protected void doTest(Function<PsiField[], PsiField[]> eqFunction,
                        Function<PsiField[], PsiField[]> hFunction,
                        Function<PsiField[], PsiField[]> nnFunction,
                        boolean insertOverride) {
    doTest(eqFunction, hFunction, nnFunction, insertOverride, false);
  }

  protected void doTest(Function<PsiField[], PsiField[]> eqFunction,
                        Function<PsiField[], PsiField[]> hFunction,
                        Function<PsiField[], PsiField[]> nnFunction,
                        boolean insertOverride, boolean useAccessors) {
    configureByFile("/codeInsight/generateEquals/before" + getTestName(false) + ".java");
    performTest(eqFunction, hFunction, nnFunction, insertOverride, useAccessors);
    checkResultByFile("/codeInsight/generateEquals/after" + getTestName(false) + ".java");
  }

  private void performTest(Function<PsiField[], PsiField[]> equals,
                                  Function<PsiField[], PsiField[]> hashCode,
                                  Function<PsiField[], PsiField[]> nonNull,
                                  boolean insertOverride, 
                                  boolean useAccessors) {
    JavaCodeStyleSettings.getInstance(getProject()).GENERATE_FINAL_LOCALS = true;
    JavaCodeStyleSettings.getInstance(getProject()).INSERT_OVERRIDE_ANNOTATION = insertOverride;
    PsiElement element = getFile().findElementAt(getEditor().getCaretModel().getOffset());
    if (element == null) return;
    PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (aClass == null) return;
    PsiField[] fields = aClass.getFields();
    new GenerateEqualsHelper(getProject(), aClass, equals.fun(fields), hashCode.fun(fields), nonNull.fun(fields), false, useAccessors).invoke();
    FileDocumentManager.getInstance().saveAllDocuments();
  }

  private static PsiField[] getIndexed(PsiField[] fields, int[] indices) {
    ArrayList<PsiField> result = new ArrayList<>();
    for (int indice : indices) {
      result.add(fields[indice]);
    }
    return result.toArray(PsiField.EMPTY_ARRAY);
  }
}
