package com.intellij.codeInsight;

import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.codeInsight.intention.impl.ImplementAbstractMethodHandler;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;

import java.util.Collection;
import java.util.Collections;

/**
 * @author ven
 */
public class OverrideImplementTest extends LightCodeInsightTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  public void testSimple() throws Exception { doTest(true); }
  public void testAnnotation() throws Exception { doTest(true); }
  public void testIncomplete() throws Exception { doTest(false); }
  public void testSubstitutionInTypeParametersList() throws Exception { doTest(false); }
  public void testTestMissed() throws Exception { doTest(false); }
  public void testWildcard() throws Exception { doTest(false); }

  public void testLongFinalParameterList() throws Exception {
    CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(getProject()).clone();

    try {
      CommonCodeStyleSettings javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);
      codeStyleSettings.RIGHT_MARGIN = 80;
      javaSettings.KEEP_LINE_BREAKS = true;
      codeStyleSettings.GENERATE_FINAL_PARAMETERS = true;
      javaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;

      CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(codeStyleSettings);

      doTest(false);
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
  }

  public void testLongParameterList() throws Exception {
    CodeStyleSettings codeStyleSettings = CodeStyleSettingsManager.getSettings(getProject()).clone();

    try {
      CommonCodeStyleSettings javaSettings = codeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE);
      codeStyleSettings.RIGHT_MARGIN = 80;
      javaSettings.KEEP_LINE_BREAKS = false;
      codeStyleSettings.GENERATE_FINAL_PARAMETERS = false;
      javaSettings.METHOD_PARAMETERS_WRAP = CommonCodeStyleSettings.WRAP_ON_EVERY_ITEM;
      CodeStyleSettingsManager.getInstance(getProject()).setTemporarySettings(codeStyleSettings);

      doTest(false);
    }
    finally {
      CodeStyleSettingsManager.getInstance(getProject()).dropTemporarySettings();
    }
  }

  public void testClone() throws Exception {
    doTest(false);
  }
  
  public void testOnTheLineWithExistingExpression() throws Exception {
    doTest(false);
  }

  public void testImplementedConstructorsExcluded() throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/overrideImplement/" + name + ".java");
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement context = getFile().findElementAt(offset);
    PsiClass psiClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    assert psiClass != null;

    final Collection<MethodSignature> signatures = OverrideImplementUtil.getMethodSignaturesToOverride(psiClass);
    final Collection<String> strings = ContainerUtil.map(signatures, new Function<MethodSignature, String>() {
      public String fun(MethodSignature signature) { return signature.toString(); }
    });

    assertTrue(strings.toString(), strings.contains("HierarchicalMethodSignatureImpl: A([PsiType:String])"));
    assertFalse(strings.toString(), strings.contains("HierarchicalMethodSignatureImpl: A([])"));
  }

  public void testEnumConstant() throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/overrideImplement/before" + name + ".java");
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement context = getFile().findElementAt(offset);
    PsiMethod psiMethod = PsiTreeUtil.getParentOfType(context, PsiMethod.class);
    assert psiMethod != null;
    final PsiClass aClass = psiMethod.getContainingClass();
    assert aClass != null && aClass.isEnum();
    final PsiField[] fields = aClass.getFields();
    new ImplementAbstractMethodHandler(getProject(), getEditor(), psiMethod).implementInClass(fields);
    checkResultByFile("/codeInsight/overrideImplement/after" + name + ".java");
  }
  
  private void doTest(boolean copyJavadoc) throws Exception {
    String name = getTestName(false);
    configureByFile("/codeInsight/overrideImplement/before" + name + ".java");
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement context = getFile().findElementAt(offset);
    PsiClass psiClass = PsiTreeUtil.getParentOfType(context, PsiClass.class);
    assert psiClass != null;
    PsiClassType[] implement = psiClass.getImplementsListTypes();
    final PsiClass superClass = implement.length == 0 ? psiClass.getSuperClass() : implement[0].resolve();
    assert superClass != null;
    PsiMethod method =  superClass.getMethods()[0];
    final PsiMethodMember member2Override = new PsiMethodMember(method,
                                                                TypeConversionUtil.getSuperClassSubstitutor(superClass, psiClass,
                                                                                                            PsiSubstitutor.EMPTY));
    OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(getEditor(), psiClass, Collections.singletonList(member2Override),
                                                                 copyJavadoc, true);
    checkResultByFile("/codeInsight/overrideImplement/after" + name + ".java");
 }
}