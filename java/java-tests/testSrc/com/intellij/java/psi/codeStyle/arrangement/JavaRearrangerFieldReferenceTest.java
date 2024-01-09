// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.*;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.*;

public class JavaRearrangerFieldReferenceTest extends AbstractJavaRearrangerTest {
  private final List<StdArrangementMatchRule> defaultFieldsArrangement =
    List.of(rule(FIELD, STATIC, FINAL), rule(FIELD, PUBLIC),
            rule(FIELD, PROTECTED), rule(FIELD, PACKAGE_PRIVATE),
            rule(FIELD, PRIVATE));

  public void test_keep_referenced_package_private_field_before_public_one_which_has_reference_through_binary_expression() {
    doTest("""
             public class TestRunnable {
                 int i = 1;
                 public int j = i + 1;
                 public int k = 3;
                 public int m = 23;
             }
             """, """
             public class TestRunnable {
                 public int k = 3;
                 public int m = 23;
                 int i = 1;
                 public int j = i + 1;
             }
             """, defaultFieldsArrangement);
  }

  public void test_keep_referenced_fields_before_those_who_has_reference_through_binary_expression() {
    doTest("""
             public class javaTest {
                 int i1 = 1;
                 protected int i2 = i1 + 4;
             }
             """, """
             public class javaTest {
                 int i1 = 1;
                 protected int i2 = i1 + 4;
             }
             """, defaultFieldsArrangement);
  }

  public void test_keep_referenced_static_fields_before_those_who_has_reference_through_binary_expression() {
    doTest("""
             public class CodeFormatTest {
                     private static String PREFIX = "prefix.";
                     public static String NAME = PREFIX + "name";
                     private static String PRIVATE_NAME = PREFIX + "private name";
                     public static String TEST = "OK!";
                     public static String BOOK = "ATLAS";
             }
             """, """
             public class CodeFormatTest {
                     public static String TEST = "OK!";
                     public static String BOOK = "ATLAS";
                     private static String PREFIX = "prefix.";
                     public static String NAME = PREFIX + "name";
                     private static String PRIVATE_NAME = PREFIX + "private name";
             }
             """, defaultFieldsArrangement);
  }

  public void test_keep_referenced_static_fields_before_those_who_has_direct_reference() {
    doTest("""
             public class CodeFormatTest {
                     private static String PREFIX = "prefix.";
                     public static String NAME = PREFIX;
             }
             """, """
             public class CodeFormatTest {
                     private static String PREFIX = "prefix.";
                     public static String NAME = PREFIX;
             }
             """, defaultFieldsArrangement);
  }

  public void test_keep_referenced_fields_before_those_who_has_direct_reference() {
    doTest("""
             public class CodeFormatTest {
                     private String PREFIX = "prefix.";
                     public String NAME = PREFIX;
             }
             """, """
             public class CodeFormatTest {
                     private String PREFIX = "prefix.";
                     public String NAME = PREFIX;
             }
             """, defaultFieldsArrangement);
  }

  public void test_keep_referenced_fields_before_those_who_has_reference_through_polyadic_expression() {
    doTest("""
             public class CodeFormatTest {
                     private String PREFIX = "prefix.";
                     public String NAME = "ololo" + "bobob" + "line" + PREFIX + "ququ";
             }
             """, """
             public class CodeFormatTest {
                     private String PREFIX = "prefix.";
                     public String NAME = "ololo" + "bobob" + "line" + PREFIX + "ququ";
             }
             """, defaultFieldsArrangement);
  }

  public void test_keep_referenced_field_before_who_has_reference_through_parenthesized_nested_binary_expression() {
    doTest("""
             public class TestRunnable {
                 int i = 3;
                 public int j = (1 + i);
             }
             """, """
             public class TestRunnable {
                 int i = 3;
                 public int j = (1 + i);
             }
             """, defaultFieldsArrangement);
  }

  public void test_keep_referenced_fields_before_those_who_has_reference_through_nested_binary_expression() {
    doTest("""
             public class TestRunnable {
                 int i = 3;
                 public int j = (1 + 2 + (5 + (5 + (5 + i))) + (1 + (i + 1)) + (3 + i) + 5) + 4;
             }
             """, """
             public class TestRunnable {
                 int i = 3;
                 public int j = (1 + 2 + (5 + (5 + (5 + i))) + (1 + (i + 1)) + (3 + i) + 5) + 4;
             }
             """, defaultFieldsArrangement);
  }

  public void test_multiple_references_on_instance_fields() {
    doTest("""
             public class TestRunnable {
                 int i = 3;
                 int k = 12;
                 public int j = (1 + 2 + (5 + (5 + (5 + i))) + (1 + (i + 1 + k)) + (3 + i) + 5) + 4;
                 public int q = 64;
             }
             """, """
             public class TestRunnable {
                 public int q = 64;
                 int i = 3;
                 int k = 12;
                 public int j = (1 + 2 + (5 + (5 + (5 + i))) + (1 + (i + 1 + k)) + (3 + i) + 5) + 4;
             }
             """, defaultFieldsArrangement);
  }

  public void test_field_initializer_has_reference_to_method() {
    doTest("""
             public class TestRunnable {
                 public int foo() {
                     return 15;
                 }

                 public int q = 64 + foo();
                 int i = 3;
                 int k = 12;
             }
             """, """
             public class TestRunnable {
                 public int q = 64 + foo();
                 int i = 3;
                 int k = 12;

                 public int foo() {
                     return 15;
                 }
             }
             """, List.of(rule(CLASS), rule(FIELD, PUBLIC),
                          rule(FIELD, PACKAGE_PRIVATE), rule(METHOD, PUBLIC)));
  }

  public void test_illegal_field_reference_arranged_to_legal() {
    doTest("""
             public class Alfa {
                 int i = 3;
                 public int j = i + 1 + q;
                 int q = 2 + 3;
                 public int r = 3;
             }
             """, """
             public class Alfa {
                 public int r = 3;
                 int i = 3;
                 int q = 2 + 3;
                 public int j = i + 1 + q;
             }
             """, defaultFieldsArrangement);
  }

  public void test_field_references_work_ok_with_enums() {
    doTest("""
             public class Q {
                 private static final Q A = new Q(Q.E.EC);
                 private static final Q B = new Q(Q.E.EB);
                 private static final Q C = new Q(Q.E.EA);
                 private static final Q D = new Q(Q.E.EA);
                 private final E e;
                 private static final int seven = 7;

                 private Q(final Q.E e) {
                     this.e = e;
                 }

                 public static enum E {
                     EA,
                     EB,
                     EC,
                 }
             }
             """, """
             public class Q {
                 private static final Q A = new Q(Q.E.EC);
                 private static final Q B = new Q(Q.E.EB);
                 private static final Q C = new Q(Q.E.EA);
                 private static final Q D = new Q(Q.E.EA);
                 private static final int seven = 7;
                 private final E e;

                 private Q(final Q.E e) {
                     this.e = e;
                 }

                 public static enum E {
                     EA,
                     EB,
                     EC,
                 }
             }
             """, defaultFieldsArrangement);
  }

  public void test_IDEA_123733() {
    doTest("""
             class First {
                 protected int test = 12;
             }

             class Second extends First {
                 void test() {}

                 private int q = test;
                 public int t = q;
             }
             """, """
             class First {
                 protected int test = 12;
             }

             class Second extends First {
                 private int q = test;
                 public int t = q;

                 void test() {}
             }
             """, defaultFieldsArrangement);
  }

  public void test_IDEA_123875() {
    doTest("""
             public class RearrangeFail {

                 public static final byte[] ENTITIES_END = "</entities>".getBytes();
                 private final Element entitiesEndElement = new Element(ENTITIES_END);

                 public static final byte[] ENTITIES_START = "<entities>".getBytes();
                 private final Element entitiesStartElement = new Element(ENTITIES_START);

             }
             """, """
             public class RearrangeFail {

                 public static final byte[] ENTITIES_END = "</entities>".getBytes();
                 public static final byte[] ENTITIES_START = "<entities>".getBytes();
                 private final Element entitiesEndElement = new Element(ENTITIES_END);
                 private final Element entitiesStartElement = new Element(ENTITIES_START);

             }
             """, List.of(rule(PUBLIC, STATIC, FINAL), rule(PRIVATE)));
  }

  public void test_IDEA_125099() {
    doTest("""
             public class test {

                 private int a = 2;

                 public static final String TEST = "1";
                 public static final String SHOULD_BE_IN_BETWEEN = "2";
                 public static final String USERS_ROLE_ID_COLUMN = TEST;
             }
             """, """
             public class test {

                 public static final String TEST = "1";
                 public static final String SHOULD_BE_IN_BETWEEN = "2";
                 public static final String USERS_ROLE_ID_COLUMN = TEST;
                 private int a = 2;
             }
             """, List.of(rule(PUBLIC, STATIC, FINAL), rule(PRIVATE)));
  }

  public void test_IDEA_128071() {
    doTest("""

             public class FormatTest {
                 public int a = 3;
                 private static final String FACEBOOK_CLIENT_ID = "";
                 public static final String FACEBOOK_OAUTH_URL = "".concat(FACEBOOK_CLIENT_ID).concat("");
             }
             """, """

             public class FormatTest {
                 private static final String FACEBOOK_CLIENT_ID = "";
                 public static final String FACEBOOK_OAUTH_URL = "".concat(FACEBOOK_CLIENT_ID).concat("");
                 public int a = 3;
             }
             """, List.of(rule(PUBLIC, STATIC, FINAL), rule(PRIVATE, STATIC, FINAL),
                          rule(PUBLIC)));
  }

  public void test_field_dependency_through_method_call() {
    doTest("""

             public class TmpTest {
                 private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
                 static final String SUB_MESSAGE_REQUEST_SNAPSHOT = create(1);

                 private static String create(int i) {
                     return Integer.toString(i + EMPTY_OBJECT_ARRAY.length);
                 }

                 public static void main(String[] args) {
                     System.out.println(SUB_MESSAGE_REQUEST_SNAPSHOT);
                 }
             }
             """, """

             public class TmpTest {
                 private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
                 static final String SUB_MESSAGE_REQUEST_SNAPSHOT = create(1);

                 public static void main(String[] args) {
                     System.out.println(SUB_MESSAGE_REQUEST_SNAPSHOT);
                 }

                 private static String create(int i) {
                     return Integer.toString(i + EMPTY_OBJECT_ARRAY.length);
                 }
             }
             """, List.of(rule(FIELD), rule(PRIVATE, FIELD),
                          rule(PUBLIC, METHOD), rule(PRIVATE, METHOD)));
  }

  public void test_only_dependencies_withing_same_initialization_scope() {
    doTest("""

             public class TestArrangementBuilder {
                 private String theString = "";
                 private static final TestArrangement DEFAULT = new TestArrangementBuilder().build();

                 public TestArrangement build() {
                     return new TestArrangement(theString);
                 }

                 public class TestArrangement {
                     private final String theString;

                     public TestArrangement() {
                         this("");
                     }

                     public TestArrangement(@NotNull String aString) {
                         theString = aString;
                     }
                 }
             }
             """, """

             public class TestArrangementBuilder {
                 private static final TestArrangement DEFAULT = new TestArrangementBuilder().build();
                 private String theString = "";

                 public TestArrangement build() {
                     return new TestArrangement(theString);
                 }

                 public class TestArrangement {
                     private final String theString;

                     public TestArrangement() {
                         this("");
                     }

                     public TestArrangement(@NotNull String aString) {
                         theString = aString;
                     }
                 }
             }
             """, List.of(rule(PUBLIC, STATIC, FINAL), rule(PRIVATE, STATIC, FINAL),
                          rule(PRIVATE, FINAL), rule(PRIVATE)));
  }

  public void testIdea264100() {
    doTest("""

             public class Test {
                 private static final String AAA = "aaa";
                 static final String BBB = AAA;
                 static final String CCC = BBB;
                 private static final Object O2 = "";
                 public static final Object O1 = "";
                 public static final Object DR = "";
                 private static final Object DA = DR;
                 private static final Object B1 = O2.toString() + DA;
                 private static final Object B2 = O2.toString() + DA;
                 private static final Object B3 = O1.toString() + DA;
                 private static final Object B4 = O1.toString() + DA;
             }
             """, """

             public class Test {
                 public static final Object O1 = "";
                 public static final Object DR = "";
                 private static final String AAA = "aaa";
                 static final String BBB = AAA;
                 static final String CCC = BBB;
                 private static final Object O2 = "";
                 private static final Object DA = DR;
                 private static final Object B1 = O2.toString() + DA;
                 private static final Object B2 = O2.toString() + DA;
                 private static final Object B3 = O1.toString() + DA;
                 private static final Object B4 = O1.toString() + DA;
             }
             """, List.of(rule(STATIC, FINAL), rule(PRIVATE, STATIC, FINAL)));
  }

  public void testIdea218936() {
    doTest("""

             public class TestOne {
                 int value;
                 public int a = 0, b = value;
             }
             """, """

             public class TestOne {
                 int value;
                 public int a = 0, b = value;
             }
             """, List.of(rule(PUBLIC), rule(PACKAGE_PRIVATE)));
  }
}
