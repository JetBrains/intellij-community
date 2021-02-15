// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.resolve;

import com.intellij.lang.jvm.JvmParameter;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LightResolveTestCase;
import org.jetbrains.annotations.NotNull;

public class ResolveRecordMethodsTest extends LightResolveTestCase {
  
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
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
    PsiType type = targetField.getType();
    assertEquals(PsiType.INT, type);

    PsiJavaFile file = (PsiJavaFile)getFile();

    PsiClass record = file.getClasses()[0];
    assertNavigatesToFirstRecordComponent(record, target);
    assertTrue(targetField.hasAnnotation("F"));
    assertFalse(targetField.hasAnnotation("M"));
  }

  public void testCompactConstructor() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiParameter);
    PsiParameter parameter = (PsiParameter)target;
    assertEquals(PsiType.INT, parameter.getType());

    PsiJavaFile file = (PsiJavaFile)getFile();

    PsiClass record = file.getClasses()[0];
    assertNavigatesToFirstRecordComponent(record, target);
    PsiMethod[] constructors = record.getConstructors();
    assertEquals(1, constructors.length);
    JvmParameter[] parameters = constructors[0].getParameters();
    assertEquals(1, parameters.length);
    assertEquals(parameters[0], parameter);
  }

  public void testCanonicalConstructor() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiClass);
    PsiMethod[] constructors = ((PsiClass)target).getConstructors();
    assertEquals(1, constructors.length);
    PsiMethod constructor = constructors[0];
    assertTrue(constructor instanceof LightRecordCanonicalConstructor);
    int offset = getEditor().getCaretModel().getOffset();
    PsiNewExpression expr = PsiTreeUtil.findElementOfClassAtOffset(getFile(), offset, PsiNewExpression.class, false);
    assertEquals(constructor, expr.resolveConstructor());
  }

  private static void assertNavigatesToFirstRecordComponent(PsiClass record, PsiElement target) {
    PsiRecordComponent[] components = record.getRecordComponents();
    assertSize(1, components);
    assertEquals(target.getTextOffset(), components[0].getTextOffset());
  }

  public void testExistingMethod() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    assertFalse(target instanceof LightRecordMethod);
    PsiClass aClass = ((PsiMethod)target).getContainingClass();
    PsiMethod[] methods = aClass.getMethods();
    assertEquals(3, methods.length);
  }
}
