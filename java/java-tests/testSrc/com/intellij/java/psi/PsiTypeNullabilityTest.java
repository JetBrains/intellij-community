// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.NullabilityAnnotationInfo;
import com.intellij.codeInsight.NullabilitySource;
import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.TypeNullability;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiCapturedWildcardType;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.util.JavaTypeNullabilityUtil;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.intellij.java.codeInspection.DataFlowInspectionTestCase.addJSpecifyNullMarked;
import static com.intellij.java.codeInspection.DataFlowInspectionTestCase.setupTypeUseAnnotations;

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

  private static void assertNullability(@NotNull String expectedDeclared, @NotNull String expectedValue, @NotNull PsiType type) {
    assertEquals(expectedDeclared, type.getNullability().toString());
    assertEquals(expectedValue, JavaTypeNullabilityUtil.getValueNullability(type).toString());
  }

  /**
   * Registers the JSpecify annotations, including {@code @NullnessUnspecified}, which is the only unspecified nullness that
   * {@link JavaTypeNullabilityUtil#getValueNullability} looks through.
   */
  private void setupJSpecifyAnnotations() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);
    myFixture.addClass("""
                         package org.jspecify.annotations;
                         import java.lang.annotation.*;

                         @Target(ElementType.TYPE_USE) public @interface NullnessUnspecified { }""");
  }

  public void testTypeParameterUnspecifiedBoundOverNotNull() {
    setupJSpecifyAnnotations();
    PsiType type = configureAndGetFieldType("""
      import org.jspecify.annotations.NullnessUnspecified;

      class A<T extends @NullnessUnspecified Object> {
        T foo;
      }
      """);
    // Object is not a type parameter, so there is no further bound to walk and the unspecified nullness stays
    assertNullability("UNKNOWN (inherited @NullnessUnspecified)", "UNKNOWN (inherited @NullnessUnspecified)", type);
  }

  public void testTypeParameterUnspecifiedBoundOverNullable() {
    setupJSpecifyAnnotations();
    PsiType type = configureAndGetFieldType("""
      import org.jspecify.annotations.Nullable;
      import org.jspecify.annotations.NullnessUnspecified;

      class A<P extends @Nullable Object, T extends @NullnessUnspecified P> {
        T foo;
      }
      """);
    // the declared nullability keeps the unspecified nullness (type-argument containment relies on it),
    // but a value of type T may definitely be null
    assertNullability("UNKNOWN (inherited @NullnessUnspecified)", "NULLABLE (inherited @Nullable)", type);
  }

  /**
   * The bound walk is deliberately restricted to JSpecify's {@code @NullnessUnspecified}: the same shape written with another
   * framework's unspecified nullness keeps the old behaviour.
   */
  public void testUnknownNullabilityBoundIsNotLookedThrough() {
    PsiType type = configureAndGetFieldType("""
      import org.jetbrains.annotations.Nullable;
      import org.jetbrains.annotations.UnknownNullability;

      class A<P extends @Nullable Object, T extends @UnknownNullability P> {
        T foo;
      }
      """);
    assertNullability("UNKNOWN (inherited @UnknownNullability)", "UNKNOWN (inherited @UnknownNullability)", type);
  }

  public void testUseSiteUnspecifiedOverNullableBound() {
    setupJSpecifyAnnotations();
    PsiType type = configureAndGetFieldType("""
      import org.jspecify.annotations.Nullable;
      import org.jspecify.annotations.NullnessUnspecified;

      class A<P extends @Nullable Object> {
        @NullnessUnspecified P foo;
      }
      """);
    assertNullability("UNKNOWN (@NullnessUnspecified)", "NULLABLE (inherited @Nullable)", type);
  }

  public void testUseSiteUnspecifiedOnClassTypeIgnoresContainer() {
    setupJSpecifyAnnotations();
    PsiType type = configureAndGetFieldType("""
      @org.jspecify.annotations.NullMarked
      class A {
        @org.jspecify.annotations.NullnessUnspecified String foo;
      }
      """);
    // String is not a type parameter, so there is no bound chain to walk and the not-null container must not leak in
    assertNullability("UNKNOWN (@NullnessUnspecified)", "UNKNOWN (@NullnessUnspecified)", type);
  }

  public void testTypeParameterUnspecifiedBoundAmongTwoSupertypes() {
    setupJSpecifyAnnotations();
    PsiType type = configureAndGetFieldType("""
      import org.jspecify.annotations.Nullable;
      import org.jspecify.annotations.NullnessUnspecified;

      class A<T extends @Nullable CharSequence & @NullnessUnspecified Comparable<T>> {
        T foo;
      }
      """);
    // Comparable is not a type parameter, so the unspecified nullness is opaque here and intersect keeps UNKNOWN
    assertNullability("UNKNOWN (inherited @NullnessUnspecified)", "UNKNOWN (inherited @NullnessUnspecified)", type);
  }

  private @NotNull Nullability captureUpperBoundNullability(@NotNull String wildcardBound) {
    setupJSpecifyAnnotations();
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.UnknownNullability;
      import org.jspecify.annotations.NullMarked;
      import org.jspecify.annotations.Nullable;
      import org.jspecify.annotations.NullnessUnspecified;

      @NullMarked
      class A {
        interface Super<T extends @Nullable Object> {
          T get();
        }

        static void test(Super<? extends %s Object> s) {
          s.get(<caret>);
        }
      }
      """.formatted(wildcardBound));
    PsiCapturedWildcardType captured = assertInstanceOf(type, PsiCapturedWildcardType.class);
    return captured.getUpperBound().getNullability().nullability();
  }

  /**
   * A JSpecify unspecified nullness is a distinct third state and wins over the nullable bound of the type parameter,
   * so the capture stays unspecified instead of turning nullable.
   */
  public void testCaptureOfUnspecifiedWildcardOverNullableTypeParameterBound() {
    assertEquals(Nullability.UNKNOWN, captureUpperBoundNullability("@NullnessUnspecified"));
  }

  public void testCaptureOfNullableWildcardOverNullableTypeParameterBound() {
    assertEquals(Nullability.NULLABLE, captureUpperBoundNullability("@Nullable"));
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
      import org.jetbrains.annotations.*;
      
      class X<T extends @Nullable Object> {
        native X<@NotNull T> foo();
      
        @NotNullByDefault
        static void test(X<?> x) {
          <caret>x;
        }
      }
      """);
    assertEquals("X<?>", type.getCanonicalText());
    assertEquals("NULLABLE (inherited @Nullable)", ((PsiClassType)type).getParameters()[0].getNullability().toString());
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
  
  public void testMalformedPackageInfo() {
    myFixture.addFileToProject("org/example/package-info.java", """
      @NotNullByDefault
      package org.example2;
      
      import org.jetbrains.annotations.NotNullByDefault;""");
    PsiFile clsFile = myFixture.addFileToProject("org/example/A.java", "package org.example; class A {}");
    PsiElement context = ((PsiJavaFile)clsFile).getClasses()[0];
    NullabilityAnnotationInfo info = NullableNotNullManager.getInstance(getProject()).findDefaultTypeUseNullability(context);
    assertNotNull(info);
    TypeNullability typeNullability = info.toTypeNullability();
    assertEquals("NOT_NULL (@NotNullByDefault on package org.example2)", typeNullability.toString());
  }
  
  public void testFBoundResolveUnderNotNull() {
    myFixture.addClass("""
      package org.jetbrains.annotations;
      
      public @interface NotNullByDefault {}
      """);
    myFixture.configureByText("Test.java", """
      import org.jetbrains.annotations.NotNullByDefault;
      
      @NotNullByDefault
      interface RestClient2 {
          default void test(RequestHeadersUriSpec<?> spec2) {
              spec2.uri().attributes().retrieve();
          }

          interface UriSpec<S extends RequestHeadersSpec<?>> {
              S uri();
          }
      
          interface RequestHeadersSpec<S extends RequestHeadersSpec<S>> {
              S attributes();
              Object retrieve();
          }
      
          interface RequestHeadersUriSpec<S extends RequestHeadersSpec<S>> extends UriSpec<S>, RequestHeadersSpec<S> {
          }
      }
      """);
    myFixture.checkHighlighting();
  }

  public void testInstantiateWithNullable() {
    addJSpecifyNullMarked(myFixture);
    setupTypeUseAnnotations("org.jspecify.annotations", myFixture);

    PsiFile file = myFixture.configureByText("Test.java", """
      import org.jspecify.annotations.NullMarked;
      import org.jspecify.annotations.Nullable;
      
      @NullMarked
      class JSpecifySameInstanceGenericInheritedBound {
        interface Tag {}
      
        interface Box<E extends @Nullable Object> {}
      
        interface Source<V extends @Nullable Object> {
          Box<V> create();
          void acceptNullable(Box<@Nullable V> box);
        }
      
        interface Derived<V extends @Nullable Object & @Nullable Tag> extends Source<V> {
          default void use() {
            acceptNullab<caret>le(create());
          }
        }
      }
      """);
    PsiMethodCallExpression methodCallExpression =
      PsiTreeUtil.getParentOfType(file.findElementAt(myFixture.getCaretOffset()), PsiMethodCallExpression.class);

    //it is a usual pattern to get expected parameter type
    JavaResolveResult result = methodCallExpression.resolveMethodGenerics();
    PsiSubstitutor substitutor = result.getSubstitutor();
    PsiMethod method = (PsiMethod)result.getElement();
    PsiType firstParameterType = method.getParameterList().getParameters()[0].getType();
    PsiType expectedParameterType = substitutor.substitute(firstParameterType);

    assertEquals("JSpecifySameInstanceGenericInheritedBound.Box<V>", expectedParameterType.getCanonicalText());
    PsiType parameterType = ((PsiClassType)expectedParameterType).getParameters()[0];
    assertEquals("V", parameterType.getCanonicalText());
    assertEquals("NULLABLE (@Nullable)", parameterType.getNullability().toString());
  }

  public void testInstantiatedWithUnspecifiedDeclared() {
    assertEquals(TypeNullability.NULLABLE_MANDATED, TypeNullability.UNKNOWN.instantiatedWith(TypeNullability.NULLABLE_MANDATED));
    assertEquals(TypeNullability.NOT_NULL_KNOWN, TypeNullability.UNKNOWN.instantiatedWith(TypeNullability.NOT_NULL_KNOWN));
    TypeNullability nullableBound = TypeNullability.NULLABLE_MANDATED.inherited();
    assertEquals(nullableBound, TypeNullability.UNKNOWN.instantiatedWith(nullableBound));
  }

  public void testSubstitutorCaptureFromUnmarkedScope() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.Nullable;

      class X<T> {
        native T foo();

        static void test(X<? extends @Nullable CharSequence> x) {
          x.foo(<caret>);
        }
      }
      """);
    assertEquals("UNKNOWN (NONE)", type.getNullability().toString());
  }

  public void testSubstitutorFromUnmarkedScope() {
    PsiType type = configureAndGetExpressionType("""
      import org.jetbrains.annotations.Nullable;

      class X<T> {
        native T foo();

        static void test(X<@Nullable CharSequence> x) {
          x.foo(<caret>);
        }
      }
      """);
    assertEquals("NULLABLE (@Nullable)", type.getNullability().toString());
  }
}
