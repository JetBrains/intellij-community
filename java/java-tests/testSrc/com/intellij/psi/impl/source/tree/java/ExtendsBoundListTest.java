package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 *  @author dsl
 */
public class ExtendsBoundListTest extends LightCodeInsightTestCase {
  public void testRemoveBoundFromFront() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        typeParameter.getExtendsList().getReferenceElements()[0].delete();
      }
    });

    check();
  }
  public void testRemoveBoundFromEnd() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        typeParameter.getExtendsList().getReferenceElements()[1].delete();
      }
    });

    check();
  }
  public void testRemoveBoundFromMiddle() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        typeParameter.getExtendsList().getReferenceElements()[1].delete();
      }
    });

    check();
  }

  public void testAddBoundInTheMiddle() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = typeParameter.getExtendsList();
    final JavaPsiFacade manager = getJavaFacade();
    final PsiClass clonableClass = manager.findClass("java.lang.Cloneable");
    assertNotNull(clonableClass);
    final PsiJavaCodeReferenceElement reference = manager.getElementFactory().createClassReferenceElement(clonableClass);
    extendsList.addAfter(reference, extendsList.getReferenceElements()[0]);
    check();
  }
  public void testAddBoundInFront() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = typeParameter.getExtendsList();
    final JavaPsiFacade manager = getJavaFacade();
    final PsiClass clonableClass = manager.findClass("java.lang.Cloneable");
    assertNotNull(clonableClass);
    final PsiJavaCodeReferenceElement reference = manager.getElementFactory().createClassReferenceElement(clonableClass);
    extendsList.addBefore(reference, extendsList.getReferenceElements()[0]);
    check();
  }

  public void testAddBoundInEnd() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = typeParameter.getExtendsList();
    final JavaPsiFacade manager = getJavaFacade();
    final PsiClass clonableClass = manager.findClass("java.lang.Cloneable");
    assertNotNull(clonableClass);
    final PsiJavaCodeReferenceElement reference = manager.getElementFactory().createClassReferenceElement(clonableClass);
    extendsList.addBefore(reference, null);
    check();
  }


  public void testAddBound() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = typeParameter.getExtendsList();
    final JavaPsiFacade manager = getJavaFacade();
    final PsiClass clonableClass = manager.findClass(CommonClassNames.JAVA_LANG_RUNNABLE);
    assertNotNull(clonableClass);
    final PsiJavaCodeReferenceElement reference = manager.getElementFactory().createClassReferenceElement(clonableClass);
    extendsList.add(reference);
    check();
  }



  private void check() throws Exception {
    outputFile(getTestName(true) + "_after.java");
  }

  private PsiTypeParameter getTypeParameter() throws Exception {
    inputFile(getTestName(true) + ".java");
    final PsiClass aClass = ((PsiJavaFile)getFile()).getClasses()[0];
    final PsiTypeParameter typeParameter = aClass.getTypeParameters()[0];
    return typeParameter;
  }

  private void outputFile(String filename) throws Exception {
    checkResultByFile("/psi/impl/extendsBoundList/" + filename);
  }

  private void inputFile(String filename) throws Exception {
    configureByFile("/psi/impl/extendsBoundList/" + filename);
  }
}
