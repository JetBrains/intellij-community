// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.psi.resolve;

import com.intellij.lang.jvm.JvmParameter;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiRecordComponent;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.impl.light.LightRecordCanonicalConstructor;
import com.intellij.psi.impl.light.LightRecordMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.DumbModeTestUtils;
import com.intellij.testFramework.LightResolveFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.LightResolveFixtureKt.lightResolveFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveRecordMethodsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture);

  private PsiElement resolve() {
    PsiReference ref = resolveFixture.get().findReferenceAtCaret("method/records/" + resolveFixture.get().getTestName(false) + ".java");
    return ReadAction.computeBlocking(() -> ref.resolve());
  }

  @Test
  public void testRecordComponent() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiMethod);
      PsiMethod targetMethod = (PsiMethod)target;
      PsiType returnType = targetMethod.getReturnType();
      assertEquals(PsiTypes.intType().createArrayType(), returnType);

      PsiJavaFile file = (PsiJavaFile)myFixture.getFile();

      PsiClass outerClass = file.getClasses()[0];
      PsiClass record = outerClass.getInnerClasses()[0];
      PsiRecordComponent[] components = record.getRecordComponents();
      assertThat(components).hasSize(1);
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
    });
  }

  @Test
  public void testRecordField() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiField);
      PsiField targetField = (PsiField)target;
      PsiType type = targetField.getType();
      assertEquals(PsiTypes.intType(), type);

      PsiJavaFile file = (PsiJavaFile)myFixture.getFile();

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
    });
  }

  @Test
  public void testCompactConstructor() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiParameter);
      PsiParameter parameter = (PsiParameter)target;
      assertEquals(PsiTypes.intType(), parameter.getType());

      PsiJavaFile file = (PsiJavaFile)myFixture.getFile();

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
    });
  }

  @Test
  public void testCanonicalConstructor() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiClass);
      PsiMethod[] constructors = ((PsiClass)target).getConstructors();
      assertEquals(1, constructors.length);
      PsiMethod constructor = constructors[0];
      assertTrue(constructor instanceof LightRecordCanonicalConstructor);
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      PsiNewExpression expr = PsiTreeUtil.findElementOfClassAtOffset(myFixture.getFile(), offset, PsiNewExpression.class, false);
      assertEquals(constructor, expr.resolveConstructor());
    });
  }

  private static void assertNavigatesToFirstRecordComponent(PsiClass record, PsiElement target) {
    PsiRecordComponent[] components = record.getRecordComponents();
    assertThat(components).hasSize(1);
    assertEquals(target.getTextOffset(), components[0].getTextOffset());
  }

  @Test
  public void testExistingMethod() {
    PsiElement target = resolve();
    ReadAction.runBlocking(() -> {
      assertTrue(target instanceof PsiMethod);
      assertFalse(target instanceof LightRecordMethod);
      PsiClass aClass = ((PsiMethod)target).getContainingClass();
      PsiMethod[] methods = aClass.getMethods();
      assertEquals(3, methods.length);
    });
  }

  @Test
  public void testMethodsDumbMode() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
      myFixture.configureByText("CustomAnnotation.java", """
        package org.example.a;
        
        public interface @CustomAnnotation {
        }
        
        """);
      PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("DumbRecord.java",
            """
              package org.example.b;
              
              import org.example.a.CustomAnnotation;
              import java.util.List;
              
              public record DumbRecord(String a, String b, @CustomAnnotation List<@CustomAnnotation String> c) {}
              """);
      ReadAction.runBlocking(() -> {
        PsiClass psiClass = file.getClasses()[0];
        //3 fields + 1 canonical constructor
        PsiMethod[] methods = psiClass.getMethods();
        assertEquals(4, methods.length);
        for (PsiMethod method : methods) {
          if (!method.isConstructor()) {
            assertNotNull(method.getReturnType());
          }
          else {
            for (PsiParameter parameter : method.getParameterList().getParameters()) {
              assertNotNull(parameter.getType());
            }
          }
        }
      });
    });
  }

  @Test
  public void testMethodsDumbModeWithExplicitConstructor() {
    LightJavaCodeInsightTestFixture myFixture = resolveFixture.get().getFixture();
    DumbModeTestUtils.runInDumbModeSynchronously(myFixture.getProject(), () -> {
      String text = """
        import java.util.List;
        
        record R2(List<String> part1, String part2) implements Runnable {
          R1(List<String> part1, String part2) {
            this(part1.toString().trim(), part2);
          }
        }
        """;
      PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("DumbRecord.java", text);
      ReadAction.runBlocking(() -> {
        PsiClass psiClass = file.getClasses()[0];
        PsiMethod[] methods = psiClass.getMethods();
        //1 constructor + 2 component methods
        assertEquals(3, methods.length);
      });
    });
  }
}
