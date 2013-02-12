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

import java.util.ArrayList;

/**
 * @author yole
 */
public abstract class GenerateEqualsTestCase extends LightCodeInsightTestCase {
  protected void doTest(int[] equals, int[] hashCode, int[] nonNull, boolean insertOverride) throws Exception {
    configureByFile("/codeInsight/generateEquals/before" + getTestName(false) + ".java");
    performTest(equals, hashCode, nonNull, insertOverride);
    checkResultByFile("/codeInsight/generateEquals/after" + getTestName(false) + ".java");
  }

  private static void performTest(int[] equals, int[] hashCode, int[] nonNull, boolean insertOverride) {
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
      new GenerateEqualsHelper(getProject(), aClass, getIndexed(fields, equals), getIndexed(fields, hashCode), getIndexed(fields, nonNull),
                               false)
        .invoke();
      FileDocumentManager.getInstance().saveAllDocuments();
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
  }

  private static PsiField[] getIndexed(PsiField[] fields, int[] indices) {
    ArrayList<PsiField> result = new ArrayList<PsiField>();
    for (int indice : indices) {
      result.add(fields[indice]);
    }
    return result.toArray(new PsiField[result.size()]);
  }
}
