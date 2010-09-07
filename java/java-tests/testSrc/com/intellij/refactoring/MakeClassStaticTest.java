/**
 * @author ven
 */
package com.intellij.refactoring;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.refactoring.makeStatic.MakeClassStaticProcessor;
import com.intellij.refactoring.makeStatic.MakeStaticUtil;
import com.intellij.refactoring.makeStatic.Settings;
import com.intellij.refactoring.util.ParameterTablePanel;
import com.intellij.testFramework.LightCodeInsightTestCase;

import java.util.ArrayList;

public class MakeClassStaticTest extends LightCodeInsightTestCase {
  private static final String TEST_ROOT = "/refactoring/makeClassStatic/";

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimple() throws Exception { perform(); }

  public void testSimpleWithFields() throws Exception { performWithFields(); }

  public void testFieldInitializerMovedToConstructor() throws Exception { performWithFields(); }

  public void testQualifiedThisInSibling() throws Exception { perform(); }

  public void testIDEADEV3247() throws Exception { perform(); }

  public void testIDEADEV11595() throws Exception { perform(); }

  public void testIDEADEV12762() throws Exception { perform(); }

  public void testNewExpressionQualifications() throws Exception {perform();}

  public void testRegularReference() throws Exception {
    perform();
  }

  public void testFieldWithPrefix() throws Exception {
    final CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(getProject());
    String oldPrefix = settings.FIELD_NAME_PREFIX;
    settings.FIELD_NAME_PREFIX = "my";
    try {
      performWithFields();
    }
    finally {
      settings.FIELD_NAME_PREFIX = oldPrefix;
    }
  }

  private void perform() throws Exception {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiClass);
    PsiClass aClass = (PsiClass)element;

    boolean addClassParameter = MakeStaticUtil.isParameterNeeded(aClass);

    new MakeClassStaticProcessor(
            getProject(),
            aClass,
            new Settings(true, addClassParameter ? "anObject" : null, null)).run();
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }

  private void performWithFields() throws Exception {
    configureByFile(TEST_ROOT + getTestName(true) + ".java");
    PsiElement element = TargetElementUtilBase.findTargetElement(myEditor, TargetElementUtilBase.ELEMENT_NAME_ACCEPTED);
    assertTrue(element instanceof PsiClass);
    PsiClass aClass = (PsiClass)element;
    final ArrayList<ParameterTablePanel.VariableData> parametersForFields = new ArrayList<ParameterTablePanel.VariableData>();
    final boolean addClassParameter = MakeStaticUtil.buildVariableData(aClass, parametersForFields);

    new MakeClassStaticProcessor(
            getProject(),
            aClass,
            new Settings(true, addClassParameter ? "anObject" : null,
                         parametersForFields.toArray(
                           new ParameterTablePanel.VariableData[parametersForFields.size()]))).run();
    checkResultByFile(TEST_ROOT + getTestName(true) + "_after.java");
  }
}
