// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilitySource;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.util.TypeAnnotationPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ref.GCUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the equality of {@link NullabilitySource.ExplicitAnnotation}.
 */
@TestApplication
public final class NullabilitySourceEqualityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

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

  @Test
  public void testStubAndAst() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("Stubbed.java", TEXT);
    ReadAction.runBlocking(() -> {
      PsiClass aClass = ((PsiJavaFile)file).getClasses()[0];
      List<TypeNullability> fromStub = nullabilities(aClass);
      assertFalse(file.isContentsLoaded());

      ASTNode node = file.getNode();
      GCUtil.tryGcSoftlyReachableObjects();
      assertTrue((boolean)file.withGreenStubTreeOrAst(_ -> false, _ -> true));
      List<TypeNullability> fromAst = nullabilities(aClass);

      for (int i = 0; i < fromStub.size(); i++) {
        assertEquals(Nullability.NULLABLE, fromStub.get(i).nullability(), "Nullability #" + i);
        assertEquals(fromStub.get(i), fromAst.get(i), "Nullability #" + i);
        assertEquals(fromStub.get(i).hashCode(), fromAst.get(i).hashCode(), "Nullability #" + i);
      }
      assertNotNull(node);
    });
  }

  @Test
  public void testTwoBounds() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("Stubbed.java", TEXT);
    NullabilitySource source = ReadAction.computeBlocking(() -> returnType(file.getClasses()[0], "twoBounds").getNullability().source());
    NullabilitySource.ExtendsBound bound = assertInstanceOf(NullabilitySource.ExtendsBound.class, source);
    NullabilitySource.MultiSource multiSource = assertInstanceOf(NullabilitySource.MultiSource.class, bound.boundSource());
    assertThat(multiSource.sources()).hasSize(2);
  }

  @Test
  public void testCopyFromText() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("Stubbed.java", TEXT);
    ReadAction.runBlocking(() -> {
      PsiClass aClass = file.getClasses()[0];
      PsiMethod method = aClass.findMethodsByName("qualified", false)[0];
      PsiType copy = JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.@Nullable String", method);
      TypeNullability original = method.getReturnType().getNullability();
      assertEquals(original.nullability(), copy.getNullability().nullability());
      assertThat(copy.getNullability()).isNotEqualTo(original);
    });
  }

  @Test
  public void testModifierListAnnotation() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("A.java", """
      import org.jetbrains.annotations.*;

      class A {
        @Nullable String first() { return null; }
        @Nullable String second() { return null; }
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiClass aClass = file.getClasses()[0];
      TypeNullability first = returnType(aClass, "first").getNullability();
      assertEquals(Nullability.NULLABLE, first.nullability());
      assertEquals(first, returnType(aClass, "first").getNullability());
      assertThat(returnType(aClass, "second").getNullability()).isNotEqualTo(first);
    });
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

  @Test
  public void testVarargsTypeArgument() {
    assertStubAndAstNullable("void m(List<@Nullable String>... args) {}", aClass ->
      ((PsiClassType)((PsiArrayType)parameterType(aClass)).getComponentType()).getParameters()[0]);
  }

  @Test
  public void testQualifiedVarargs() {
    assertStubAndAstNullable("void m(java.lang.@Nullable String... args) {}", aClass ->
      ((PsiArrayType)parameterType(aClass)).getComponentType());
  }

  @Test
  public void testTwoDimensionalArrayTypeArgument() {
    assertStubAndAstNullable("List<@Nullable String>[][] f;", aClass -> {
      PsiArrayType outer = (PsiArrayType)aClass.findFieldByName("f", false).getType();
      PsiArrayType inner = (PsiArrayType)outer.getComponentType();
      return ((PsiClassType)inner.getComponentType()).getParameters()[0];
    });
  }

  @Test
  public void testCStyleArrayTypeArgument() {
    assertStubAndAstNullable("List<@Nullable String> f[];", aClass -> {
      PsiArrayType array = (PsiArrayType)aClass.findFieldByName("f", false).getType();
      return ((PsiClassType)array.getComponentType()).getParameters()[0];
    });
  }

  @Test
  public void testCompoundField() {
    assertStubAndAstNullable("List<@Nullable String> a, b;", aClass ->
      ((PsiClassType)aClass.findFieldByName("b", false).getType()).getParameters()[0]);
  }

  @Test
  public void testCompoundFieldWithFewerDimensions() {
    assertStubAndAstNullable("List<@Nullable String> @Nullable [] a[], b;", aClass ->
      ((PsiClassType)((PsiArrayType)aClass.findFieldByName("b", false).getType()).getComponentType()).getParameters()[0]);
  }

  @Test
  public void testCompoundFieldWithMoreDimensions() {
    assertStubAndAstNullable("List<@Nullable String> a, b[];", aClass -> {
      PsiArrayType array = (PsiArrayType)aClass.findFieldByName("b", false).getType();
      return ((PsiClassType)array.getComponentType()).getParameters()[0];
    });
  }

  @Test
  public void testCompoundFieldSharesAnnotation() {
    assertStubAndAstNullable("List<@Nullable String> a, b;", aClass -> {
      PsiType first = ((PsiClassType)aClass.findFieldByName("a", false).getType()).getParameters()[0];
      PsiType second = ((PsiClassType)aClass.findFieldByName("b", false).getType()).getParameters()[0];
      assertEquals(first.getNullability(), second.getNullability());
      return second;
    });
  }

  private static final String INNER_CLASSES = """
    class Outer { class Inner {} }
    @java.lang.annotation.Target(java.lang.annotation.ElementType.TYPE_USE) @interface Other {}
    """;

  @Test
  public void testInnerType() {
    assertStubAndAstNullable(INNER_CLASSES + "List<Outer.@Nullable Inner> f;", aClass ->
      ((PsiClassType)aClass.findFieldByName("f", false).getType()).getParameters()[0]);
  }

  @Test
  public void testAnnotatedOuterAndInnerType() {
    assertStubAndAstNullable(INNER_CLASSES + "List<@Other Outer.@Nullable Inner> f;", aClass ->
      ((PsiClassType)aClass.findFieldByName("f", false).getType()).getParameters()[0]);
  }

  @Test
  public void testAnnotatedQualifierPaths() {
    assertDistinctStubAndAstPaths("List<Stubbed.@Other Outer.@Nullable Inner> f;");
  }

  @Test
  public void testAnnotatedOuterAndInnerPaths() {
    assertDistinctStubAndAstPaths("List<@Other Outer.@Nullable Inner> f;");
  }

  /**
   * Checks the paths of the two annotations in the type argument of field {@code f}.
   */
  private void assertDistinctStubAndAstPaths(@NotNull String field) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("Stubbed.java", """
      import org.jetbrains.annotations.*;
      import java.util.List;

      class Stubbed {
        %s
        %s
      }
      """.formatted(INNER_CLASSES, field));
    ReadAction.runBlocking(() -> {
      PsiClass aClass = ((PsiJavaFile)file).getClasses()[0];
      List<TypeAnnotationPath> fromStub = annotationPaths(aClass.findFieldByName("f", false).getType());
      assertFalse(file.isContentsLoaded());

      ASTNode node = file.getNode();
      GCUtil.tryGcSoftlyReachableObjects();
      assertTrue((boolean)file.withGreenStubTreeOrAst(_ -> false, _ -> true));
      List<TypeAnnotationPath> fromAst = annotationPaths(aClass.findFieldByName("f", false).getType());
      assertThat(fromStub).hasSize(2);
      assertNotNull(fromStub.get(0));
      assertEquals(fromStub, fromAst);
      assertThat(fromStub.get(1)).isNotEqualTo(fromStub.get(0));
      assertNotNull(node);
    });
  }

  private static @NotNull List<TypeAnnotationPath> annotationPaths(@NotNull PsiType type) {
    PsiClassType argument = (PsiClassType)((PsiClassType)type).getParameters()[0];
    PsiJavaCodeReferenceElement reference = ((PsiClassReferenceType)argument).getReference();
    return SyntaxTraverser.psiTraverser(reference.getParent()).filter(PsiAnnotation.class).map(TypeAnnotationPath::of).toList();
  }

  private void assertStubAndAstNullable(@NotNull String member, @NotNull Function<PsiClass, PsiType> typeGetter) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFileImpl file = (PsiFileImpl)myFixture.addFileToProject("Stubbed.java", """
      import org.jetbrains.annotations.*;
      import java.util.List;

      class Stubbed {
        %s
      }
      """.formatted(member));
    ReadAction.runBlocking(() -> {
      PsiClass aClass = ((PsiJavaFile)file).getClasses()[0];
      PsiType fromStub = typeGetter.apply(aClass);
      assertFalse(file.isContentsLoaded());
      assertEquals(Nullability.NULLABLE, fromStub.getNullability().nullability(), "Stub: " + fromStub.getCanonicalText(true));

      ASTNode node = file.getNode();
      GCUtil.tryGcSoftlyReachableObjects();
      assertTrue((boolean)file.withGreenStubTreeOrAst(_ -> false, _ -> true));
      PsiType fromAst = typeGetter.apply(aClass);
      assertEquals(Nullability.NULLABLE, fromAst.getNullability().nullability(), "AST: " + fromAst.getCanonicalText(true));
      assertEquals(fromStub.getCanonicalText(true), fromAst.getCanonicalText(true));
      assertEquals(fromStub.getNullability(), fromAst.getNullability());
      assertNotNull(node);
    });
  }

  private static @NotNull PsiType parameterType(@NotNull PsiClass aClass) {
    return aClass.findMethodsByName("m", false)[0].getParameterList().getParameters()[0].getType();
  }
}
