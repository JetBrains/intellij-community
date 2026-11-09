// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class OverloadedVarargsMethodInspectionTest
{
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> new OverloadedVarargsMethodInspection());

  @Test
  public void testOneWarning() {
    inspectionFixture.get().doTest("""
             class Overload {
               public void overload() {}
               public void overload(int p1) {}
               public void overload(int p1, String p2) {}
               public void /*Overloaded varargs method 'overload()'*/overload/**/(int p1, String p2, String... p3) {}
             }""");
  }

  @Test
  public void testWarnWhenSuperMethod() {
    inspectionFixture.get().doTest("""
             class Super {
               public void method() {}
             }
             class Overload extends Super {
               public void /*Overloaded varargs method 'method()'*/method/**/(String... ss) {}
             }""");
  }

  @Test
  public void testOverridingMethod() {
    inspectionFixture.get().doTest("""
             interface Base {
               void test(String... ss);
             }
             class Impl implements Base {
               public void test(String... ss) {}
             }""");
  }

  @Test
  public void testGenericMethods() {
    inspectionFixture.get().doTest("""
             interface Foo<T> {
                     void makeItSo(T command, int... values);
                 }
                 class Bar implements Foo<String> {
                     public void makeItSo(final String command, final int... values) {
                     }
                 }""");
  }

  @Test
  public void testNoWarningBecauseOfTypes() {
    inspectionFixture.get().doTest("""
             class Overload {
               public void method() {}
               public void method(int p1) {}
               public void method(int p1, int p2) {}
               public void method(int p1, String p2, int p3) {}
               public void method(int p1, String p2, String p3, int p4) {}
               public void method(int p1, String p2, String... p3) {}
             }""");
  }
  
  @Test
  public void testNoWarningBecauseOfTypes2() {
    inspectionFixture.get().doTest("""
             import java.util.*;
             final class CompositeRequestDataValueProcessor {
             
                 private final List<? extends RequestDataValueProcessor> processors;
             
                 public CompositeRequestDataValueProcessor(final RequestDataValueProcessor... processors) {
                     this(Arrays.asList(processors));
                 }
             
                 public CompositeRequestDataValueProcessor(final List<? extends RequestDataValueProcessor> processors) {
                     this.processors = processors;
                 }
             }
             
             interface RequestDataValueProcessor {}
             """);
  }

  @Test
  public void testWarningForConvertibleArgumentTypes() {
    inspectionFixture.get().doTest("""
             class Overload {
               public void method(Number p1, String p2) {}
               public void /*Overloaded varargs method 'method()'*/method/**/(Integer p1, String p2, String... p3) {}
             }""");
  }

  @Test
  public void testWarningWithOneArgument() {
    inspectionFixture.get().doTest("""
             class Overload {
               public void method() {}
               public void /*Overloaded varargs method 'method()'*/method/**/(String... p1) {}
             }""");
  }
}
