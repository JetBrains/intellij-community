package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author dsl
 */
public class BindToGenericClassTest extends GenericsTestCase {
  private boolean myOldFQNamesSetting;

  protected void setUp() throws Exception {
    super.setUp();
    final ModifiableRootModel rootModel = setupGenericSampleClasses();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        rootModel.commit();
      }
    });
    final CodeStyleSettings currentSettings = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings();

    myOldFQNamesSetting = currentSettings.USE_FQ_CLASS_NAMES;
    currentSettings.USE_FQ_CLASS_NAMES = true;
  }

  protected void tearDown() throws Exception {
    final CodeStyleSettings currentSettings = CodeStyleSettingsManager.getInstance(myProject).getCurrentSettings();
    currentSettings.USE_FQ_CLASS_NAMES = myOldFQNamesSetting;
    super.tearDown();
  }

  public void testReferenceElement() throws Exception {
    final JavaPsiFacade manager = getJavaFacade();
    final PsiClass classA = manager.getElementFactory().createClassFromText("class A extends List<String>{}", null).getInnerClasses()[0];
    final PsiClass classTestList = manager.findClass("test.List", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(classTestList);
    classA.getExtendsList().getReferenceElements()[0].bindToElement(classTestList);
    assertEquals("class A extends test.List<String>{}", classA.getText());
  }

  public void testReference() throws Exception {
    final JavaPsiFacade manager = getJavaFacade();
    final PsiExpression psiExpression = manager.getElementFactory().createExpressionFromText("List", null);
    final PsiClass classTestList = manager.findClass("test.List", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(classTestList);
    final PsiElement result = ((PsiReferenceExpression) psiExpression).bindToElement(classTestList);
    assertEquals("test.List", result.getText());
  }
}
