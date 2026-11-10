package com.intellij.java.codeInspection;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class CheckerNullityTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("""
      package org.checkerframework.checker.nullness.qual;
      import java.lang.annotation.*;
      
      @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
      public @interface NonNull {}
      @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER})
      public @interface Nullable {}
      @Target(ElementType.TYPE_USE)
      public @interface MonotonicNonNull {}
      """);

    myFixture.addClass("""
      package org.checkerframework.checker.nullness.compatqual;
      import java.lang.annotation.*;
      
      public @interface NonNullDecl {}
      public @interface NullableDecl {}
      
      @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) public @interface NonNullType { }
      @Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) public @interface NullableType { }
      """);
    
    myFixture.addClass("""
      package org.checkerframework.framework.qual;
      import java.lang.annotation.*;
      public @interface DefaultQualifier {
        Class<? extends Annotation> value();
        TypeUseLocation[] locations() default {TypeUseLocation.ALL};
      }
      public @interface DefaultQualifiers { DefaultQualifier[] value() default {}; }
      
      public enum TypeUseLocation {
        FIELD, LOCAL_VARIABLE, RESOURCE_VARIABLE, EXCEPTION_PARAMETER, RECEIVER, PARAMETER, RETURN, LOWER_BOUND, EXPLICIT_LOWER_BOUND,
        IMPLICIT_LOWER_BOUND, UPPER_BOUND, EXPLICIT_UPPER_BOUND, IMPLICIT_UPPER_BOUND, OTHERWISE, ALL
      }
      """);
  }

  @Test
  public void test_own_qual_annotations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.qual.*;
      interface Foo {
        @Nullable Object foo();
        @NonNull Object bar();
      }""");
    ReadAction.runBlocking(() -> {
      assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[0]));
      assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[1]));
    });
  }

  @Test
  public void test_own_compatqual_annotations() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.compatqual.*;
      interface Foo {
        @NullableDecl Object foo();
        @NullableType Object foo2();
        @NonNullDecl Object bar();
        @NonNullType Object bar2();
      }""");
    ReadAction.runBlocking(() -> {
      assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[0]));
      assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[1]));
      assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[2]));
      assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[3]));
    });
  }

  @Test
  public void test_default_qualifier_on_class() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.qual.*;
      import org.checkerframework.framework.qual.*;
      @DefaultQualifier(value = Nullable.class, locations = TypeUseLocation.FIELD)
      class MyClass {
          Object nullableField = null;
          @NonNull Object nonNullField = new Object();
          Object method() {}
          @MonotonicNonNull Object foo = null;
      }""");
    ReadAction.runBlocking(() -> {
      assertTrue(NullableNotNullManager.isNotNull(clazz.getFields()[1]));
      assertTrue(NullableNotNullManager.isNullable(clazz.getFields()[0]));
      assertTrue(!NullableNotNullManager.isNullable(clazz.getMethods()[0]) && !NullableNotNullManager.isNotNull(clazz.getMethods()[0]));
      assertTrue(!NullableNotNullManager.isNullable(clazz.getFields()[2]) && !NullableNotNullManager.isNotNull(clazz.getFields()[2]));
    });
  }

  @Test
  public void test_default_qualifiers_on_parent_package() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("foo/package-info.java", """
      @DefaultQualifiers({
        @DefaultQualifier(value = Nullable.class, locations = TypeUseLocation.RETURN),
        @DefaultQualifier(value = Nullable.class, locations = {TypeUseLocation.PARAMETER})})
      package foo;

      import org.checkerframework.checker.nullness.qual.*;
      import org.checkerframework.framework.qual.*;
      """);

    PsiClass clazz = myFixture.addClass("""
          package foo.bar;
          class MyClass {
              Object field = null;
              Object method(Object param) {}
          }""");
    ReadAction.runBlocking(() -> {
      assertTrue(!NullableNotNullManager.isNullable(clazz.getFields()[0]) && !NullableNotNullManager.isNotNull(clazz.getFields()[0]));
      assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[0]));
      assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[0].getParameterList().getParameters()[0]));
    });
  }

  @Test
  public void test_default_nonnull_qualifier() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.qual.NonNull;
      import org.checkerframework.framework.qual.DefaultQualifier;
      import org.checkerframework.framework.qual.TypeUseLocation;
    
      @DefaultQualifier(value= NonNull.class, locations = TypeUseLocation.ALL)
      class Test {
          private void testDefault(String param){ }
      }""");
    ReadAction.runBlocking(() -> assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[0].getParameterList().getParameters()[0])));
  }

  @Test
  public void test_type_use_array() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.qual.*;
    
      class Test {
          @Nullable
          Object[] array(String param){ }
          @Nullable Object plain(String param){ }
      }""");
    ReadAction.runBlocking(() -> {
      assertFalse(NullableNotNullManager.isNullable(clazz.getMethods()[0]));
      assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[1]));
    });
  }

  @Test
  public void test_type_parameter_use() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.qual.*;
      import org.checkerframework.framework.qual.*;

      @DefaultQualifier(NonNull.class)
      interface Test<X extends @Nullable Object> {
        String test(X x);
        X test(String x);
      }""");
    ReadAction.runBlocking(() -> {
      assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[0]));
      assertFalse(NullableNotNullManager.isNotNull(clazz.getMethods()[1]));
      assertFalse(NullableNotNullManager.isNotNull(clazz.getMethods()[0].getParameterList().getParameters()[0]));
      assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[1].getParameterList().getParameters()[0]));
    });
  }
}
