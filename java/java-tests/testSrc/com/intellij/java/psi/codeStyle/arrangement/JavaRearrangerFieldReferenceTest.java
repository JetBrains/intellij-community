// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.codeStyle.arrangement;

import com.intellij.psi.codeStyle.arrangement.match.StdArrangementMatchRule;

import java.util.List;

import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.CLASS;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.FIELD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.EntryType.METHOD;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.FINAL;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PACKAGE_PRIVATE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PRIVATE;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PROTECTED;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.PUBLIC;
import static com.intellij.psi.codeStyle.arrangement.std.StdArrangementTokens.Modifier.STATIC;

@SuppressWarnings("ALL")
public class JavaRearrangerFieldReferenceTest extends AbstractJavaRearrangerTest {
  private final List<StdArrangementMatchRule> defaultFieldsArrangement =
    List.of(rule(FIELD, STATIC, FINAL), rule(FIELD, PUBLIC),
            rule(FIELD, PROTECTED), rule(FIELD, PACKAGE_PRIVATE),
            rule(FIELD, PRIVATE));
  
  public void testFieldInitializerDependsOnFieldItSelf() {
    doTest("""
             class Improbabilia {
                 private static final Thread thread = new Thread(new Runnable() {
                     @Override
                     public void run() {
                         while (true) {
                             synchronized (thread) {
                                 // field thread would depend on itself if
                                 // dependency checker visited anonymous class
                                 System.out.println(thread);
                             }
                         }
                     }
                 });
             
                 static {
                     thread.start();
                 }
             }""", defaultFieldsArrangement);
  }
  
  public void testMethodReferences() {
    // IDEA-311599
    doTest("""
             public class Foo {
                 private final Runnable mFooRunnable1 = this::runFooRunnable2;
                 private final Runnable mFooRunnable2 = makeFooRunnable2();
                 public Foo() {
                 }
                 private Runnable makeFooRunnable2() {
                     return new Runnable() {
                         @Override
                         public void run() {
                             mFooRunnable1.run();
                         }
                     };
                 }
                 private void runFooRunnable2() {
                     mFooRunnable2.run();
                 }
             }
             """, defaultFieldsArrangement);

    // IDEA-314824
    doTest("""
             public record Test() {
                 static final Integer TEMP0 = 3;
                 static final Integer TEMP1 = run(ITest::temp2);
                 static final Integer TEMP2 = TEMP0 + 2 + TEMP1;
                 static Integer run(final Supplier<Integer> supplier) {
                     return 4;
                 }
                 interface ITest {
                     static int temp2() {
                         return Test.TEMP2;
                     }
                 }
             }
             """, defaultFieldsArrangement);

    // IDEA-341366
    doTest("""
             import java.util.function.Consumer;
             public class Showcase {
                 Consumer<? super Boolean> foo = this::bar;
                 Object baz;
                 private void bar(Boolean really) {
                     System.out.println(foo);
                 }
             }
             """, defaultFieldsArrangement);
  }
  
  public void testDependenciesBetweenFields() {
    // IDEA-286442
    doTest("""
             class Scratch {
                 private static final String COMMA_DELIMITER = ",";
                 private static final String UUID_REGEXP = "([0-9A-Za-z-]+)";
                 private static final String URI_REGEXP = "(http://<some_url_pattern>+,?)";
                 private static final String URIS_REGEXP = "(" + URI_REGEXP + "|" + "(\\"" + URI_REGEXP + "+\\"))";
                 private static final String TITLE_REGEXP = "((.+)|(\\".+\\"))";
                 private static final String FULL_REGEXP = UUID_REGEXP + COMMA_DELIMITER
                         + URIS_REGEXP + COMMA_DELIMITER
                         + TITLE_REGEXP;
                 private static final Pattern PATTERN = Pattern.compile(FULL_REGEXP);
             }
             """, defaultFieldsArrangement);

    // IDEA-293864
    doTest("""
             import java.util.List;
             public class TestClass {
                 static int int1 = 1;
                 static List<Integer> numbers = List.of(int1);
                 static int int2 = 2;
                 static boolean int2Present = numbers.contains(int2);
             }
             """, defaultFieldsArrangement);

    // IDEA-280036
    doTest("""
             class FormatterDependencyIssue {
                public static final String AA = "aa";
                public static final String ZZ = "zz";
                public static final String COMPOSED = AA + ZZ;
                public static String AA_NON_FINAL = "aa";
                public static String ZZ_NON_FINAL = "zz";
                public final String AA_NON_STATIC = "aa";
                public final String ZZ_NON_STATIC = "zz";
                public String AA_NON_STATIC_NON_FINAL = "aa";
                public String ZZ_NON_STATIC_NON_FINAL = "zz";
                public static String COMPOSED_NON_FINAL = AA_NON_FINAL + ZZ_NON_FINAL;
                public final String COMPOSED_NON_STATIC = AA_NON_STATIC + ZZ_NON_STATIC;
                public String COMPOSED_NON_STATIC_NON_FINAL = AA_NON_STATIC_NON_FINAL + ZZ_NON_STATIC_NON_FINAL;
             }""", defaultFieldsArrangement);

    // IDEA-333223
    doTest("""
             class Foo {
                 public static final String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
                 public static final String lower = upper.toLowerCase(Locale.ROOT);
                 public static final String digits = "0123456789";
                 public static final String alphanum = lower + digits;
             }""", defaultFieldsArrangement);

    // IDEA-321048
    doTest("""
             class MyTestKO {
             
                 private static final String TENANT_ID = "tenantId";
                 private static final String ASSET_ID_NAMESPACE_1 = "assetIdNamespace1";
                 private static final String ASSET_ID_NAMESPACE_2 = "assetIdNamespace2";
                 private static final String ASSET_ID_1 = "assetId1";
                 private static final String ASSET_ID_2 = "assetId2";
                 private static final String DEVICE_ID_1 = "urn:lo:nsid:" + ASSET_ID_NAMESPACE_1 + ":" + ASSET_ID_1;
             
                 private static final String RESOURCE_ID_1 = "resourceId1";
                 private static final String RESOURCE_ID_2 = "resourceId2";
                 private static final String SOURCE_VERSION_1 = "sourceVersion1";
                 private static final String SOURCE_VERSION_2 = "sourceVersion2";
                 private static final String TARGET_VERSION_1 = "targetVersion1";
                 private static final String TARGET_VERSION_2 = "targetVersion2";
                 private static final String DEVICE_ID_2 = "urn:lo:nsid:" + ASSET_ID_NAMESPACE_2 + ":" + ASSET_ID_2;
             
                 private static final StateModel UPDATE_A = StateModel.builder()
                         .tenantId(TENANT_ID).deviceId(DEVICE_ID_1)
                         .resourceId(RESOURCE_ID_1)
                         .sourceVersion(SOURCE_VERSION_1)
                         .targetVersion(TARGET_VERSION_1)
                         .build();
                 private static final StateModel UPDATE_B = StateModel.builder()
                         .tenantId(TENANT_ID).deviceId(DEVICE_ID_2)
                         .resourceId(RESOURCE_ID_2)
                         .sourceVersion(SOURCE_VERSION_2)
                         .targetVersion(TARGET_VERSION_2)
                         .build();
             }""", defaultFieldsArrangement);

    // IDEA-291785
    doTest("""
             class RearrangeCodeBugExample {
                 int var0;
                 int var1 = var0 + 1;
                 int var2 = 2;
                 int sum = var1 + var2;
             }
             """, defaultFieldsArrangement);
  }

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

  public void test_IDEA_246100() {
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

  public void test_IDEA_218936() {
    doTest("""
             public class TestOne {
                 int value;
                 public int a = 0, b = value;
             }
             """,
           List.of(rule(PUBLIC), rule(PACKAGE_PRIVATE)));
  }
}
