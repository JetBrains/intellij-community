// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.codeInsight.*;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

public final class PsiTypeNullabilityTest extends LightJavaCodeInsightFixtureTestCase {
  public void testPrimitive() {
    TypeNullability nullTypeNullability = PsiTypes.nullType().getNullability();
    assertEquals(TypeNullability.NULLABLE_MANDATED, nullTypeNullability);
    assertEquals(Nullability.NULLABLE, nullTypeNullability.nullability());
    assertEquals(NullabilitySource.Standard.MANDATED, nullTypeNullability.source());
    
    TypeNullability intTypeNullability = PsiTypes.intType().getNullability();
    assertEquals(TypeNullability.NOT_NULL_MANDATED, intTypeNullability);
    assertEquals(Nullability.NOT_NULL, intTypeNullability.nullability());
    assertEquals(NullabilitySource.Standard.MANDATED, intTypeNullability.source());
    
    TypeNullability voidTypeNullability = PsiTypes.voidType().getNullability();
    assertEquals(TypeNullability.UNKNOWN, voidTypeNullability);
    assertEquals(Nullability.UNKNOWN, voidTypeNullability.nullability());
    assertEquals(NullabilitySource.Standard.NONE, voidTypeNullability.source());
  }

  private @NotNull PsiType configureAndGetFieldType(@Language("JAVA") String text) {
    PsiFile file = myFixture.configureByText("Test.java", text);
    return ((PsiJavaFile)file).getClasses()[0].getFields()[0].getType();
  }

  private @NotNull PsiType configureAndGetExpressionType(@Language("JAVA") String code) {
    PsiFile file = myFixture.configureByText("Test.java", code);
    PsiExpression expression = PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiExpression.class);
    PsiType type = expression.getType();
    assertNotNull(type);
    return type;
  }

  public void testSimpleUnknown() {
    PsiType type = configureAndGetFieldType("""
      class A {
        String foo;
      }
      """);
    assertEquals("UNKNOWN (NONE)", type.getNullability().toString());
  }
  
  public void testSimpleUnknownWithAnnotation() {
    PsiType type = configureAndGetFieldType("""
      @org.jetbrains.annotations.NotNullByDefault
      class A {
        @org.jetbrains.annotations.UnknownNullability String foo;
      }
      """);
    assertEquals("UNKNOWN (@UnknownNullability)", type.getNullability().toString());
  }
  
  public void testSimpleNotNull() {
    PsiType type = configureAndGetFieldType("""
      class A {
        @org.jetbrains.annotations.NotNull String foo = "";
      }
      """);
    assertEquals("NOT_NULL (@NotNull)", type.getNullability().toString());
  }
  
  public void testSimpleNullable() {
    PsiType type = configureAndGetFieldType("""
      import org.jetbrains.annotations.*;
      
      class A {
        @Nullable String foo;
      }
      """);
    assertEquals("NULLABLE (@Nullable)", type.getNullability().toString());
  }
  
  public void testContainerNotNull() {
    PsiType type = configureAndGetFieldType("""
      @org.jetbrains.annotations.NotNullByDefault
      class A {
        String foo = "";
      }
      """);
    assertEquals("NOT_NULL (@NotNullByDefault on class A)", type.getNullability().toString());
  }
  
  public void testTypeParameterSupertype() {
    PsiType type = configureAndGetFieldType("""
      import org.jetbrains.annotations.NotNull;
      
      class A<T extends @NotNull CharSequence> {
        T foo = "";
      }
      """);
    assertEquals("NOT_NULL (inherited @NotNull)", type.getNullability().toString());
  }

  public void testTypeParameterTwoSupertypes() {
    PsiType type = configureAndGetFieldType("""
      import org.jetbrains.annotations.NotNull;
      
      class A<T extends @NotNull CharSequence & @NotNull Comparable<T>> {
        T foo = "";
      }
      """);
    assertEquals("NOT_NULL (inherited [@NotNull, @NotNull])", type.getNullability().toString());
  }

  public void testTypeParameterTwoSupertypesDifferentNullability() {
    PsiType type = configureAndGetFieldType("""
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Nullable;
      
      class A<T extends @NotNull CharSequence & @Nullable Comparable<T>> {
        T foo = "";
      }
      """);
    assertEquals("NOT_NULL (inherited @NotNull)", type.getNullability().toString());
  }

  public void testTypeParameterSupertypeRecursive() {
    PsiType type = configureAndGetFieldType("""
      import org.jetbrains.annotations.NotNull;
      
      class A<T extends T> {
        T foo = "";
      }
      """);
    assertEquals("UNKNOWN (NONE)", type.getNullability().toString());
  }

  public void testArrayType() {
    PsiType type = configureAndGetFieldType("""
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Nullable;
      
      class A {
        @NotNull String @Nullable [] foo;
      }
      """);
    assertEquals("NULLABLE (@Nullable)", type.getNullability().toString());
    assertEquals("NOT_NULL (@NotNull)", type.getDeepComponentType().getNullability().toString());
  }
  
  public void testSubstitutorSimple() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.NotNull;
      
      class X<T> {
        native T foo();
      
        static void test(X<@NotNull String> x) {
          x.foo(<caret>);
        }
      }
      """);
    assertEquals("java.lang.String", type.getCanonicalText());
    assertEquals("NOT_NULL (@NotNull)", type.getNullability().toString());
  }

  public void testSubstitutorOnTypeParameter() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Nullable;
      
      class X<T> {
        native @NotNull T foo();
      
        static void test(X<@Nullable String> x) {
          x.foo(<caret>);
        }
      }
      """);
    assertEquals("java.lang.String", type.getCanonicalText());
    assertEquals("NOT_NULL (@NotNull)", type.getNullability().toString());
  }

  public void testSubstitutorOnTypeParameterArray() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Nullable;
      
      class X<T> {
        native @NotNull T foo();
      
        static void test(X<@Nullable String[]> x) {
          x.foo(<caret>);
        }
      }
      """);
    assertEquals("java.lang.String[]", type.getCanonicalText());
    assertEquals("NOT_NULL (@NotNull)", type.getNullability().toString());
  }

  public void testWildcard() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Nullable;
      
      class X<T> {
        native X<@NotNull T> foo();
      
        static void test(X<? extends @Nullable CharSequence> x) {
          <caret>x;
        }
      }
      """);
    assertEquals("X<? extends java.lang.CharSequence>", type.getCanonicalText());
    assertEquals("NULLABLE (inherited @Nullable)", ((PsiClassType)type).getParameters()[0].getNullability().toString());
  }
  
  public void testWildcardAnnotated() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.Nullable;
      
      class X<T> {
        native X<@NotNull T> foo();
      
        @SuppressWarnings("NullableProblems")
        static void test(X<@Nullable ?> x) {
          <caret>x;
        }
      }
      """);
    assertEquals("X<?>", type.getCanonicalText());
    assertEquals("NULLABLE (@Nullable)", ((PsiClassType)type).getParameters()[0].getNullability().toString());
  }
  
  public void testSubstitutorOnTypeParameterUnknown() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.UnknownNullability;
      import org.jetbrains.annotations.Nullable;
      
      class X<T> {
        native @UnknownNullability T foo();
      
        static void test(X<@Nullable String> x) {
          x.foo(<caret>);
        }
      }
      """);
    assertEquals("java.lang.String", type.getCanonicalText());
    assertEquals("UNKNOWN (@UnknownNullability)", type.getNullability().toString());
  }

  public void testSubstitutorOuter() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.NotNull;
      
      class X<T> {
        native @NotNull X<T> foo();
      
        static void test(X<@NotNull String> x) {
          x.foo(<caret>);
        }
      }
      """);
    assertEquals("X<java.lang.String>", type.getCanonicalText());
    assertEquals("NOT_NULL (@NotNull)", type.getNullability().toString());
  }

  public void testVariableTypeByExpressionType() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.Nullable;
      import org.jetbrains.annotations.NotNull;
      
      final class X<T> {
        static void test(@Nullable String @NotNull [] x) {
          <caret>x;
        }
      }
      """);
    assertEquals("NOT_NULL (@NotNull)", type.getNullability().toString());
    assertEquals("NULLABLE (@Nullable)", type.getDeepComponentType().getNullability().toString());
    type = GenericsUtil.getVariableTypeByExpressionType(type);
    assertEquals("NOT_NULL (@NotNull)", type.getNullability().toString());
    assertEquals("NULLABLE (@Nullable)", type.getDeepComponentType().getNullability().toString());
  }

  public void testEliminateWildcards() {
    PsiType type = configureAndGetFieldType("""
      import org.jetbrains.annotations.Nullable;
      import org.jetbrains.annotations.NotNull;
      import java.util.List;
      
      final class X<T> {
        @NotNull List<? extends @Nullable CharSequence> x = List.of();
      }
      """);
    assertEquals("java.util.List<? extends java.lang.CharSequence>", type.getCanonicalText());
    assertEquals("NOT_NULL (@NotNull)", type.getNullability().toString());
    assertEquals("NULLABLE (inherited @Nullable)", ((PsiClassType)type).getParameters()[0].getNullability().toString());
    type = GenericsUtil.eliminateWildcards(type);
    assertEquals("java.util.List<java.lang.CharSequence>", type.getCanonicalText());
    assertEquals("NOT_NULL (@NotNull)", type.getNullability().toString());
    assertEquals("NULLABLE (@Nullable)", ((PsiClassType)type).getParameters()[0].getNullability().toString());
  }

  public void testEliminateWildcardsArray() {
    PsiType type = configureAndGetFieldType("""
      import org.jetbrains.annotations.Nullable;
      import org.jetbrains.annotations.NotNull;
      
      final class X<T> {
        @NotNull String @Nullable [] x = {};
      }
      """);
    assertEquals("NULLABLE (@Nullable)", type.getNullability().toString());
    assertEquals("NOT_NULL (@NotNull)", type.getDeepComponentType().getNullability().toString());
    type = GenericsUtil.eliminateWildcards(type);
    assertEquals("NULLABLE (@Nullable)", type.getNullability().toString());
    assertEquals("NOT_NULL (@NotNull)", type.getDeepComponentType().getNullability().toString());
  }

  public void testNotNullInstantiationOnNullableFromDeclaration() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.Nullable;
      import org.jetbrains.annotations.NotNull;
      
      final class X<T> {
        native @Nullable T m();

        static void test(X<@NotNull String> x) {>) {
          x.m(<caret>);
        }
      }
      """);
    assertEquals("NULLABLE (@Nullable)",
                 ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0].getReturnType().getNullability().toString());
    assertEquals("NULLABLE (@Nullable)", type.getNullability().toString());
  }

  public void testNotNullInstantiationOnNullableFromSupertype() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.Nullable;
      import org.jetbrains.annotations.NotNull;
      
      final class X<T extends @Nullable Object> {
        native T m();

        static void test(X<@NotNull String> x) {>) {
          x.m(<caret>);
        }
      }
      """);
    assertEquals("NULLABLE (inherited @Nullable)",
                 ((PsiJavaFile)myFixture.getFile()).getClasses()[0].getMethods()[0].getReturnType().getNullability().toString());
    assertEquals("NOT_NULL (@NotNull)", type.getNullability().toString());
  }
  
  public void testLocalTopLevelIgnoreContainer() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.NotNull;
      import org.jetbrains.annotations.NotNullByDefault;

      @NotNullByDefault
      final class X {
        static void test() {
          String s = "";
          <caret>s;
    """);
    assertEquals("java.lang.String", type.getCanonicalText());
    assertEquals("UNKNOWN (NONE)", type.getNullability().toString());
  }
  
  public void testPackageNullabilityInfoToTypeNullability() {
    myFixture.addFileToProject("foo/package-info.java", "@org.jetbrains.annotations.NotNullByDefault package foo;");
    PsiFile clsFile = myFixture.addFileToProject("foo/A.java", "package foo; class A {}"); 
    PsiElement context = ((PsiJavaFile)clsFile).getClasses()[0];
    NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(getProject()).findDefaultTypeUseNullability(context);
    assertNotNull(info);
    TypeNullability typeNullability = info.toTypeNullability();
    assertEquals("NOT_NULL (@NotNullByDefault on package foo)", typeNullability.toString());
  }
}
