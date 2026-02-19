// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author Bas Leijdekkers
 */
public class OverloadedVarargsMethodInspectionTest extends LightJavaInspectionTestCase
{
  @Override
  protected InspectionProfileEntry getInspection() {
    return new OverloadedVarargsMethodInspection();
  }

  public void testOneWarning() {
    doTest("""
             class Overload {
               public void overload() {}
               public void overload(int p1) {}
               public void overload(int p1, String p2) {}
               public void /*Overloaded varargs method 'overload()'*/overload/**/(int p1, String p2, String... p3) {}
             }""");
  }

  public void testWarnWhenSuperMethod() {
    doTest("""
             class Super {
               public void method() {}
             }
             class Overload extends Super {
               public void /*Overloaded varargs method 'method()'*/method/**/(String... ss) {}
             }""");
  }

  public void testOverridingMethod() {
    doTest("""
             interface Base {
               void test(String... ss);
             }
             class Impl implements Base {
               public void test(String... ss) {}
             }""");
  }

  public void testGenericMethods() {
    doTest("""
             interface Foo<T> {
                     void makeItSo(T command, int... values);
                 }
                 class Bar implements Foo<String> {
                     public void makeItSo(final String command, final int... values) {
                     }
                 }""");
  }

  public void testNoWarningBecauseOfTypes() {
    doTest("""
             class Overload {
               public void method() {}
               public void method(int p1) {}
               public void method(int p1, int p2) {}
               public void method(int p1, String p2, int p3) {}
               public void method(int p1, String p2, String p3, int p4) {}
               public void method(int p1, String p2, String... p3) {}
             }""");
  }
  
  public void testNoWarningBecauseOfTypes2() {
    doTest("""
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

  public void testWarningForConvertibleArgumentTypes() {
    doTest("""
             class Overload {
               public void method(Number p1, String p2) {}
               public void /*Overloaded varargs method 'method()'*/method/**/(Integer p1, String p2, String... p3) {}
             }""");
  }

  public void testWarningWithOneArgument() {
    doTest("""
             class Overload {
               public void method() {}
               public void /*Overloaded varargs method 'method()'*/method/**/(String... p1) {}
             }""");
  }
}
