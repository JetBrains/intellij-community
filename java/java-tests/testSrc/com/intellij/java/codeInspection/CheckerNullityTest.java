package com.intellij.java.codeInspection;

import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.psi.PsiClass;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class CheckerNullityTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
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

  public void test_own_qual_annotations() {
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.qual.*;
      interface Foo {
        @Nullable Object foo();
        @NonNull Object bar();
      }""");
    assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[0]));
    assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[1]));
  }

  public void test_own_compatqual_annotations() {
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.compatqual.*;
      interface Foo {
        @NullableDecl Object foo();
        @NullableType Object foo2();
        @NonNullDecl Object bar();
        @NonNullType Object bar2();
      }""");
    assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[0]));
    assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[1]));
    assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[2]));
    assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[3]));
  }

  public void test_default_qualifier_on_class() {
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
    assertTrue(NullableNotNullManager.isNotNull(clazz.getFields()[1]));
    assertTrue(NullableNotNullManager.isNullable(clazz.getFields()[0]));
    assertTrue(!NullableNotNullManager.isNullable(clazz.getMethods()[0]) && !NullableNotNullManager.isNotNull(clazz.getMethods()[0]));
    assertTrue(!NullableNotNullManager.isNullable(clazz.getFields()[2]) && !NullableNotNullManager.isNotNull(clazz.getFields()[2]));
  }

  public void test_default_qualifiers_on_parent_package() {
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
  assertTrue(!NullableNotNullManager.isNullable(clazz.getFields()[0]) && !NullableNotNullManager.isNotNull(clazz.getFields()[0]));
  assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[0]));
  assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[0].getParameterList().getParameters()[0]));
  }

  public void test_default_nonnull_qualifier() {
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.qual.NonNull;
      import org.checkerframework.framework.qual.DefaultQualifier;
      import org.checkerframework.framework.qual.TypeUseLocation;
      
      @DefaultQualifier(value= NonNull.class, locations = TypeUseLocation.ALL)
      class Test {
          private void testDefault(String param){ }
      }""");
    assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[0].getParameterList().getParameters()[0]));
  }

  public void test_type_use_array() {
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.qual.*;
      
      class Test {
          @Nullable
          Object[] array(String param){ }
          @Nullable Object plain(String param){ }
      }""");
    assertFalse(NullableNotNullManager.isNullable(clazz.getMethods()[0]));
    assertTrue(NullableNotNullManager.isNullable(clazz.getMethods()[1]));
  }

  public void test_type_parameter_use() {
    PsiClass clazz = myFixture.addClass("""
      import org.checkerframework.checker.nullness.qual.*;
      import org.checkerframework.framework.qual.*;
      
      @DefaultQualifier(NonNull.class)
      interface Test<X extends @Nullable Object> {
        String test(X x);
        X test(String x);
      }""");
  assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[0]));
  assertFalse(NullableNotNullManager.isNotNull(clazz.getMethods()[1]));
  assertFalse(NullableNotNullManager.isNotNull(clazz.getMethods()[0].getParameterList().getParameters()[0]));
  assertTrue(NullableNotNullManager.isNotNull(clazz.getMethods()[1].getParameterList().getParameters()[0]));
  }
}
