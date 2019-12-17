// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.resolve;

import com.intellij.psi.*;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LightResolveTestCase;
import org.jetbrains.annotations.NotNull;

public class ResolveRecordMethodsTest extends LightResolveTestCase {
  
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_14;
  }

  private PsiElement resolve() {
    PsiReference ref = findReferenceAtCaret("method/records/" + getTestName(false) + ".java");
    return ref.resolve();
  }

  public void testRecordComponent() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiMethod targetMethod = (PsiMethod)target;
    assertEquals(PsiType.INT, targetMethod.getReturnType());

    PsiJavaFile file = (PsiJavaFile)getFile();

    PsiClass outerClass = file.getClasses()[0];
    PsiClass record = outerClass.getInnerClasses()[0];
    PsiRecordComponent[] components = record.getRecordComponents();
    assertSize(1, components);
    assertEquals(target.getTextOffset(), components[0].getTextOffset());
    assertFalse(targetMethod.hasAnnotation("F"));
    assertTrue(targetMethod.hasAnnotation("M"));
  }

  public void testRecordField() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiField);
    PsiField targetField = (PsiField)target;
    assertEquals(PsiType.INT, targetField.getType());

    PsiJavaFile file = (PsiJavaFile)getFile();

    PsiClass record = file.getClasses()[0];
    PsiRecordComponent[] components = record.getRecordComponents();
    assertSize(1, components);
    assertEquals(target.getTextOffset(), components[0].getTextOffset());
    assertTrue(targetField.hasAnnotation("F"));
    assertFalse(targetField.hasAnnotation("M"));
  }
}
