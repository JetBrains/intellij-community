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

import com.intellij.codeInsight.generation.GenerateEqualsHelper;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.Function;

import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class GenerateEqualsTestCase extends LightCodeInsightTestCase {
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

  private static void performTest(Function<PsiField[], PsiField[]> equals,
                                  Function<PsiField[], PsiField[]> hashCode,
                                  Function<PsiField[], PsiField[]> nonNull,
                                  boolean insertOverride, 
                                  boolean useAccessors) {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject()).clone();
    settings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS = true;
    settings.getCustomSettings(JavaCodeStyleSettings.class).INSERT_OVERRIDE_ANNOTATION = insertOverride;
    CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(settings);
    try {
      PsiElement element = getFile().findElementAt(getEditor().getCaretModel().getOffset());
      if (element == null) return;
      PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (aClass == null) return;
      PsiField[] fields = aClass.getFields();
      new GenerateEqualsHelper(getProject(), aClass, equals.fun(fields), hashCode.fun(fields), nonNull.fun(fields), false, useAccessors).invoke();
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
  }

  private static PsiField[] getIndexed(PsiField[] fields, int[] indices) {
    ArrayList<PsiField> result = new ArrayList<>();
    for (int indice : indices) {
      result.add(fields[indice]);
    }
    return result.toArray(new PsiField[result.size()]);
  }
}
