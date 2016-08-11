package com.intellij.codeInsight;

import com.intellij.codeInsight.generation.GenerateEqualsHelper;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
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
                        boolean insertOverride) throws Exception {
    doTest(fields -> getIndexed(fields, equals), fields -> getIndexed(fields, hashCode), fields -> getIndexed(fields, nonNull), insertOverride);
  }

  protected void doTest(Function<PsiField[], PsiField[]> eqFunction,
                        Function<PsiField[], PsiField[]> hFunction,
                        Function<PsiField[], PsiField[]> nnFunction,
                        boolean insertOverride) throws Exception {
    doTest(eqFunction, hFunction, nnFunction, insertOverride, false);
  }

  protected void doTest(Function<PsiField[], PsiField[]> eqFunction,
                        Function<PsiField[], PsiField[]> hFunction,
                        Function<PsiField[], PsiField[]> nnFunction,
                        boolean insertOverride, boolean useAccessors) throws Exception {
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
    settings.GENERATE_FINAL_LOCALS = true;
    settings.INSERT_OVERRIDE_ANNOTATION = insertOverride;
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
