// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilitySource;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.ref.GCUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotEquals;

/**
 * Checks the equality of {@link NullabilitySource.ExplicitAnnotation}.
 */
public final class NullabilitySourceEqualityTest extends LightJavaCodeInsightFixtureTestCase {
  private static final String TEXT = """
    import org.jetbrains.annotations.*;
    import java.util.List;

    class Stubbed {
      List<@Nullable String> field;

      <T extends @Nullable Object> T bound() { return null; }
      <T extends @Nullable Runnable & @Nullable Cloneable> T twoBounds() { return null; }
      List<? extends @Nullable String> wildcard() { return null; }
      String @Nullable [] array() { return null; }
      List<@Nullable String []> component() { return null; }
      java.lang.@Nullable String qualified() { return null; }    }
    """;

  public void testStubAndAst() {
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("Stubbed.java", TEXT);
    PsiClass aClass = ((PsiJavaFile)file).getClasses()[0];
    List<TypeNullability> fromStub = nullabilities(aClass);
    assertFalse(file.isContentsLoaded());

    ASTNode node = file.getNode();
    GCUtil.tryGcSoftlyReachableObjects();
    assertTrue(file.withGreenStubTreeOrAst(_ -> false, _ -> true));
    List<TypeNullability> fromAst = nullabilities(aClass);

    for (int i = 0; i < fromStub.size(); i++) {
      assertEquals("Nullability #" + i, Nullability.NULLABLE, fromStub.get(i).nullability());
      assertEquals("Nullability #" + i, fromStub.get(i), fromAst.get(i));
      assertEquals("Nullability #" + i, fromStub.get(i).hashCode(), fromAst.get(i).hashCode());
    }
    assertNotNull(node);
  }

  public void testTwoBounds() {
    PsiClass aClass = ((PsiJavaFile)myFixture.configureByText("Stubbed.java", TEXT)).getClasses()[0];
    NullabilitySource source = returnType(aClass, "twoBounds").getNullability().source();
    NullabilitySource.ExtendsBound bound = assertInstanceOf(source, NullabilitySource.ExtendsBound.class);
    NullabilitySource.MultiSource multiSource = assertInstanceOf(bound.boundSource(), NullabilitySource.MultiSource.class);
    assertSize(2, multiSource.sources());
  }

  public void testCopyFromText() {
    PsiClass aClass = ((PsiJavaFile)myFixture.configureByText("Stubbed.java", TEXT)).getClasses()[0];
    PsiMethod method = aClass.findMethodsByName("qualified", false)[0];
    PsiType copy = JavaPsiFacade.getElementFactory(getProject()).createTypeFromText("java.lang.@Nullable String", method);
    TypeNullability original = method.getReturnType().getNullability();
    assertEquals(original.nullability(), copy.getNullability().nullability());
    assertNotEquals(original, copy.getNullability());
  }

  public void testModifierListAnnotation() {
    PsiClass aClass = ((PsiJavaFile)myFixture.configureByText("A.java", """
      import org.jetbrains.annotations.*;

      class A {
        @Nullable String first() { return null; }
        @Nullable String second() { return null; }
      }
      """)).getClasses()[0];
    TypeNullability first = returnType(aClass, "first").getNullability();
    assertEquals(Nullability.NULLABLE, first.nullability());
    assertEquals(first, returnType(aClass, "first").getNullability());
    assertNotEquals(first, returnType(aClass, "second").getNullability());
  }

  private static @NotNull List<TypeNullability> nullabilities(@NotNull PsiClass aClass) {
    List<TypeNullability> result = new ArrayList<>();
    result.add(((PsiClassType)aClass.findFieldByName("field", false).getType()).getParameters()[0].getNullability());
    result.add(returnType(aClass, "bound").getNullability());
    result.add(returnType(aClass, "twoBounds").getNullability());
    result.add(((PsiClassType)returnType(aClass, "wildcard")).getParameters()[0].getNullability());
    result.add(returnType(aClass, "array").getNullability());
    PsiType componentArray = ((PsiClassType)returnType(aClass, "component")).getParameters()[0];
    result.add(((PsiArrayType)componentArray).getComponentType().getNullability());
    result.add(returnType(aClass, "qualified").getNullability());
    return result;
  }

  private static @NotNull PsiType returnType(@NotNull PsiClass aClass, @NotNull String name) {
    return aClass.findMethodsByName(name, false)[0].getReturnType();
  }
}
