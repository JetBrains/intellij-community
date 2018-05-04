// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase 

/**
 * @author peter
 */
class CheckerNullityTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp()
    myFixture.addClass """
package org.checkerframework.checker.nullness.qual; 
import java.lang.annotation.*;

@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) public @interface NonNull {}
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) public @interface Nullable {}
"""

    myFixture.addClass """
package org.checkerframework.checker.nullness.compatqual; 
import java.lang.annotation.*;

public @interface NonNullDecl {}
public @interface NullableDecl {}

@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) public @interface NonNullType { }
@Target({ElementType.TYPE_USE, ElementType.TYPE_PARAMETER}) public @interface NullableType { }
"""
    
    myFixture.addClass """
package org.checkerframework.framework.qual;
import java.lang.annotation.*;
public @interface DefaultQualifier {
  Class<? extends Annotation> value();
  TypeUseLocation[] locations() default {TypeUseLocation.ALL};
}
public @interface DefaultQualifiers { DefaultQualifier[] value() default {}; }

public enum TypeUseLocation { 
  FIELD, LOCAL_VARIABLE, RESOURCE_VARIABLE, EXCEPTION_PARAMETER, RECEIVER, PARAMETER, RETURN, LOWER_BOUND, EXPLICIT_LOWER_BOUND, IMPLICIT_LOWER_BOUND, UPPER_BOUND, EXPLICIT_UPPER_BOUND, IMPLICIT_UPPER_BOUND, OTHERWISE, ALL
}

"""
  }

  void "test own qual annotations"() {
    def clazz = myFixture.addClass("""
import org.checkerframework.checker.nullness.qual.*; 
interface Foo { @Nullable Object foo(); @NonNull Object bar(); }""")
    assert NullableNotNullManager.isNullable(clazz.methods[0])
    assert NullableNotNullManager.isNotNull(clazz.methods[1])
  }

  void "test own compatqual annotations"() {
    def clazz = myFixture.addClass("""
import org.checkerframework.checker.nullness.compatqual.*; 
interface Foo { 
  @NullableDecl Object foo(); 
  @NullableType Object foo2(); 
  @NonNullDecl Object bar(); 
  @NonNullType Object bar2(); 
}""")
    assert NullableNotNullManager.isNullable(clazz.methods[0])
    assert NullableNotNullManager.isNullable(clazz.methods[1])
    assert NullableNotNullManager.isNotNull(clazz.methods[2])
    assert NullableNotNullManager.isNotNull(clazz.methods[3])
  }

  void "test default qualifier on class"() {
    def clazz = myFixture.addClass """
import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.framework.qual.*;
@DefaultQualifier(value = Nullable.class, locations = TypeUseLocation.FIELD)
class MyClass {
    Object nullableField = null;
    @NonNull Object nonNullField = new Object();
    Object method() {}
}"""
    assert NullableNotNullManager.isNotNull(clazz.fields[1])
    assert NullableNotNullManager.isNullable(clazz.fields[0])
    assert !NullableNotNullManager.isNullable(clazz.methods[0]) && !NullableNotNullManager.isNotNull(clazz.methods[0])
  }
  
  void "test default qualifiers on parent package"() {
    myFixture.addFileToProject "foo/package-info.java", """
@DefaultQualifiers({
  @DefaultQualifier(value = Nullable.class, locations = TypeUseLocation.RETURN), 
  @DefaultQualifier(value = Nullable.class, locations = {TypeUseLocation.PARAMETER}))
package foo;

import org.checkerframework.checker.nullness.qual.*;
import org.checkerframework.framework.qual.*;
"""
    
    def clazz = myFixture.addClass """package foo.bar;
class MyClass {
    Object field = null;
    Object method(Object param) {}
}"""
    assert !NullableNotNullManager.isNullable(clazz.fields[0]) && !NullableNotNullManager.isNotNull(clazz.fields[0])
    assert NullableNotNullManager.isNullable(clazz.methods[0])
    assert NullableNotNullManager.isNullable(clazz.methods[0].parameterList.parameters[0])
  }
  
}
