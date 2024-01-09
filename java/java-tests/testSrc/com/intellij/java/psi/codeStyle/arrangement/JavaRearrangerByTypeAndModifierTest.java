// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.AbstractRearrangerTest.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;

public class JavaRearrangerByTypeAndModifierTest extends AbstractJavaRearrangerTest {
  public void test_complex_sample() {
    getCommonSettings().BLANK_LINES_AROUND_METHOD = 0;
    getCommonSettings().BLANK_LINES_AROUND_CLASS = 0;

    doTest("""
             class Test {
                private enum PrivateEnum {}
                protected static class ProtectedStaticInner {}
                public class PublicInner {}
                private interface PrivateInterface {}
                public abstract void abstractMethod();
                private void privateMethod() {}
                public void publicMethod() {}
                private int privateField;
                private volatile int privateVolatileField;
                public int publicField;
                public static int publicStaticField;
             }""", """
             class Test {
                public static int publicStaticField;
                public int publicField;
                private volatile int privateVolatileField;
                private int privateField;
                public abstract void abstractMethod();
                public void publicMethod() {}
                private void privateMethod() {}
                private interface PrivateInterface {}
                private enum PrivateEnum {}
                public class PublicInner {}
                protected static class ProtectedStaticInner {}
             }""", List.of(rule(FIELD, PUBLIC, STATIC), rule(FIELD, PUBLIC),
                           rule(FIELD, VOLATILE), rule(FIELD, PRIVATE),
                           rule(METHOD, ABSTRACT), rule(METHOD, PUBLIC),
                           rule(METHOD), rule(INTERFACE), rule(ENUM),
                           rule(CLASS, PUBLIC), rule(CLASS)));
  }

  public void test_instance_initialization_block_bound_to_a_field() {
    doTest("""
             class Test {
               private int i;
               public int j;
               { j = 1; }
               protected int k;
             }""", """
             class Test {
               public int j;
               protected int k;
               private int i;

               { j = 1; }
             }""", List.of(rule(FIELD, PUBLIC), rule(FIELD, PROTECTED),
                           rule(FIELD, PRIVATE)));
  }

  public void test_instance_initialization_block_as_the_first_child() {
    doTest("""
             class Test {
               { j = 1; }
               private int i;
               public int j;
               protected int k;
             }""", """
             class Test {
               public int j;
               protected int k;
               private int i;

               { j = 1; }
             }""", List.of(rule(FIELD, PUBLIC), rule(FIELD, PROTECTED),
                           rule(FIELD, PRIVATE)));
  }

  public void test_getter_is_matched_by_public_method_rule() {
    doTest("""
             class Test {
               private void test() {}

               private int count;

               private void run() {}

               public int getCount() {
                 return count;
               }

               private void compose() {}
             }
             """, """
             class Test {
               private int count;

               private void test() {}

               private void run() {}

               private void compose() {}

               public int getCount() {
                 return count;
               }
             }
             """, List.of(rule(FIELD), rule(PRIVATE, METHOD), rule(PUBLIC, METHOD)));
  }

  public void test_getter_is_matched_by_method_rule() {
    doTest("""
             class Test {
               public int getCount() {
                 return count;
               }

               class T {}

               private int count;

               private void compose() {}
             }
             """, """
             class Test {
               private int count;

               public int getCount() {
                 return count;
               }

               private void compose() {}

               class T {}
             }
             """,
           List.of(rule(FIELD), rule(METHOD)));
  }

  public void test_getter_is_not_matched_by_private_method() {
    doTest("""
             class Test {
               private int count;

               public int getCount() {
                 return count;
               }

               private void compose() {}
             }
             """, """
             class Test {
               private void compose() {}
               private int count;

               public int getCount() {
                 return count;
               }
             }
             """, List.of(rule(PRIVATE, METHOD), rule(FIELD)));
  }

  public void test_getter_is_matched_by_getter_rule() {
    doTest("""
             class Test {
               private void test() {}

               private int count;

               private void run() {}

               public int getCount() {
                 return count;
               }

               public int superRun() {}

               private void compose() {}
             }
             """, """
             class Test {
               private int count;

               public int getCount() {
                 return count;
               }

               private void test() {}

               private void run() {}

               private void compose() {}

               public int superRun() {}
             }
             """, List.of(rule(FIELD), rule(GETTER), rule(PRIVATE, METHOD),
                          rule(PUBLIC, METHOD)));
  }

  public void test_setter_is_matched_by_public_method_rule() {
    doTest("""
             class Test {
               private void test() {}

               private int count;

               private void run() {}

               public void setCount(int value) {
                 count = value;
               }

               private void compose() {}
             }
             """, """
             class Test {
               private int count;

               private void test() {}

               private void run() {}

               private void compose() {}

               public void setCount(int value) {
                 count = value;
               }
             }
             """, List.of(rule(FIELD), rule(PRIVATE, METHOD),
                          rule(PUBLIC, METHOD)));
  }

  public void test_setter_is_matched_by_method_rule() {
    doTest("""
             class Test {
               public void setCount(int value) {
                 count = value;
               }

               class T {}

               private int count;

               private void compose() {}
             }
             """, """
             class Test {
               private int count;

               public void setCount(int value) {
                 count = value;
               }

               private void compose() {}

               class T {}
             }
             """,
           List.of(rule(FIELD), rule(METHOD)));
  }

  public void test_setter_is_not_matched_by_private_method() {
    doTest("""
             class Test {
               private int count;

               public void setCount(int value) {
                 count = value;
               }

               private void compose() {}
             }
             """, """
             class Test {
               private void compose() {}
               private int count;

               public void setCount(int value) {
                 count = value;
               }
             }
             """, List.of(rule(PRIVATE, METHOD), rule(FIELD)));
  }

  public void test_setter_is_matched_by_setter_rule() {
    doTest("""
             class Test {
               private void test() {}

               private int count;

               private void run() {}

               public void setCount(int value) {
                 count = value;
               }

               public int superRun() {}

               private void compose() {}
             }
             """, """
             class Test {
               private int count;

               public void setCount(int value) {
                 count = value;
               }

               private void test() {}

               private void run() {}

               private void compose() {}

               public int superRun() {}
             }
             """, List.of(rule(FIELD), rule(SETTER), rule(PRIVATE, METHOD),
                          rule(PUBLIC, METHOD)));
  }

  public void test_setters_and_getters() {
    doTest("""
             class Test {
               private void test() {}

               private void run() {}

               public void setCount(int value) {
                 count = value;
               }

               private int count;

               public int getCount() {
                 return count;
               }

               private void compose() {}

               public int superRun() {}
             }
             """, """
             class Test {
               private int count;

               public int getCount() {
                 return count;
               }

               public void setCount(int value) {
                 count = value;
               }

               private void test() {}

               private void run() {}

               private void compose() {}

               public int superRun() {}
             }
             """, List.of(rule(FIELD), rule(GETTER), rule(SETTER),
                          rule(METHOD, PRIVATE), rule(METHOD, PUBLIC)));
  }
}
