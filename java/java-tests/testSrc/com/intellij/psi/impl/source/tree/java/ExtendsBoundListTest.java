/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.*;
import com.intellij.testFramework.LightCodeInsightTestCase;

/**
 *  @author dsl
 */
public class ExtendsBoundListTest extends LightCodeInsightTestCase {
  public void testRemoveBoundFromFront() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    WriteCommandAction.runWriteCommandAction(null, new Runnable() {
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
    final PsiClass cloneableClass = getJavaFacade().findClass("java.lang.Cloneable");
    assertNotNull(cloneableClass);
    final PsiJavaCodeReferenceElement reference = getJavaFacade().getElementFactory().createClassReferenceElement(cloneableClass);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        extendsList.addAfter(reference, extendsList.getReferenceElements()[0]);
      }
    });

    check();
  }

  public void testAddBoundInFront() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = typeParameter.getExtendsList();
    final PsiClass cloneableClass = getJavaFacade().findClass("java.lang.Cloneable");
    assertNotNull(cloneableClass);
    final PsiJavaCodeReferenceElement reference = getJavaFacade().getElementFactory().createClassReferenceElement(cloneableClass);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        extendsList.addBefore(reference, extendsList.getReferenceElements()[0]);
      }
    });

    check();
  }

  public void testAddBoundInEnd() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = typeParameter.getExtendsList();
    final PsiClass cloneableClass = getJavaFacade().findClass("java.lang.Cloneable");
    assertNotNull(cloneableClass);
    final PsiJavaCodeReferenceElement reference = getJavaFacade().getElementFactory().createClassReferenceElement(cloneableClass);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        extendsList.addBefore(reference, null);
      }
    });

    check();
  }

  public void testAddBound() throws Exception {
    final PsiTypeParameter typeParameter = getTypeParameter();
    final PsiReferenceList extendsList = typeParameter.getExtendsList();
    final PsiClass cloneableClass = getJavaFacade().findClass(CommonClassNames.JAVA_LANG_RUNNABLE);
    assertNotNull(cloneableClass);
    final PsiJavaCodeReferenceElement reference = getJavaFacade().getElementFactory().createClassReferenceElement(cloneableClass);
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        extendsList.add(reference);
      }
    });

    check();
  }

  private void check() throws Exception {
    outputFile(getTestName(true) + "_after.java");
  }

  private PsiTypeParameter getTypeParameter() throws Exception {
    inputFile(getTestName(true) + ".java");
    final PsiClass aClass = ((PsiJavaFile)getFile()).getClasses()[0];
    return aClass.getTypeParameters()[0];
  }

  private void outputFile(String filename) throws Exception {
    checkResultByFile("/psi/impl/extendsBoundList/" + filename);
  }

  private void inputFile(String filename) throws Exception {
    configureByFile("/psi/impl/extendsBoundList/" + filename);
  }
}
