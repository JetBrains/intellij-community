// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.resolve;

import com.intellij.lang.jvm.JvmParameter;
import com.intellij.psi.*;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LightResolveTestCase;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class ResolveRecordMethodsTest extends LightResolveTestCase {

  private PsiElement resolve() {
    PsiReference ref = findReferenceAtCaret("method/records/" + getTestName(false) + ".java");
    return ref.resolve();
  }

  public void testRecordComponent() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiMethod);
    PsiMethod targetMethod = (PsiMethod)target;
    PsiType returnType = targetMethod.getReturnType();
    assertEquals(PsiTypes.intType().createArrayType(), returnType);

    PsiJavaFile file = (PsiJavaFile)getFile();

    PsiClass outerClass = file.getClasses()[0];
    PsiClass record = outerClass.getInnerClasses()[0];
    PsiRecordComponent[] components = record.getRecordComponents();
    assertSize(1, components);
    assertEquals(target.getTextOffset(), components[0].getTextOffset());
    assertEquals(List.of("F", "M", "T"),
                 ContainerUtil.map(components[0].getAnnotations(), PsiAnnotation::getQualifiedName));
    assertEquals(List.of("M", "T"), 
                 ContainerUtil.map(targetMethod.getAnnotations(), PsiAnnotation::getQualifiedName));
    assertEquals(List.of("M", "T"), 
                 ContainerUtil.map(targetMethod.getModifierList().getAnnotations(), PsiAnnotation::getQualifiedName));
    assertEquals(List.of("T1"), 
                 ContainerUtil.map(returnType.getAnnotations(), PsiAnnotation::getQualifiedName));
    assertFalse(targetMethod.hasAnnotation("F"));
    assertFalse(targetMethod.getModifierList().hasAnnotation("F"));
    assertTrue(targetMethod.hasAnnotation("T"));
    assertTrue(targetMethod.getModifierList().hasAnnotation("T"));
    assertNull(targetMethod.getAnnotation("F"));
    assertTrue(targetMethod.hasAnnotation("M"));
    assertNotNull(targetMethod.getAnnotation("M"));
    assertNotNull(targetMethod.getModifierList().findAnnotation("M"));
  }

  public void testRecordField() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiField);
    PsiField targetField = (PsiField)target;
    PsiType type = targetField.getType();
    assertEquals(PsiTypes.intType(), type);

    PsiJavaFile file = (PsiJavaFile)getFile();

    PsiClass record = file.getClasses()[0];
    assertNavigatesToFirstRecordComponent(record, target);
    assertEquals(List.of("F", "T"),
                 ContainerUtil.map(targetField.getAnnotations(), PsiAnnotation::getQualifiedName));
    assertEquals(List.of("T"),
                 ContainerUtil.map(type.getAnnotations(), PsiAnnotation::getQualifiedName));
    assertTrue(targetField.hasAnnotation("F"));
    assertNotNull(targetField.getAnnotation("F"));
    assertTrue(targetField.getModifierList().hasAnnotation("F"));
    assertTrue(targetField.hasAnnotation("T"));
    assertFalse(targetField.hasAnnotation("M"));
    assertNull(targetField.getAnnotation("M"));
  }

  public void testCompactConstructor() {
    PsiElement target = resolve();
    assertTrue(target instanceof PsiParameter);
    PsiParameter parameter = (PsiParameter)target;
    assertEquals(PsiTypes.intType(), parameter.getType());

    PsiJavaFile file = (PsiJavaFile)getFile();

    PsiClass record = file.getClasses()[0];
    assertNavigatesToFirstRecordComponent(record, target);
    PsiMethod[] constructors = record.getConstructors();
    assertEquals(1, constructors.length);
    JvmParameter[] parameters = constructors[0].getParameters();
    assertEquals(1, parameters.length);
    assertEquals(parameters[0], parameter);
    assertEquals(List.of("P", "T"),
                 ContainerUtil.map(parameter.getAnnotations(), PsiAnnotation::getQualifiedName));
    assertEquals(List.of("T"),
                 ContainerUtil.map(parameter.getType().getAnnotations(), PsiAnnotation::getQualifiedName));
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
