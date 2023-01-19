// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.JavaParsingTestCase;
import com.intellij.lang.java.parser.JavaParser;

public class DeclarationParserTest extends JavaParsingTestCase {
  public DeclarationParserTest() {
    super("parser-partial/declarations");
  }

  public void testEmptyBody0() { doParserTest("{ }"); }
  public void testEmptyBody1() { doParserTest("{ "); }
  public void testEmptyBody2() { doParserTest("{ @Null }"); }

  public void testNoType() { doParserTest("{ new X(); }"); }
  public void testExtraSemicolon() { doParserTest("{ class C { }; }"); }
  public void testParameterizedClass() { doParserTest("{ public class A <T extends java.util.List> { } }"); }
  public void testPines() { doParserTest("{ class A<T extends List<String>> extends List<List<Integer>> { } }"); }
  public void testIncompleteAnnotation() { doParserTest("{ public class Foo { public void testSomething(); @Null } }"); }
  public void testClassInit() { doParserTest("{ { /*comment*/ } }"); }

  public void testEnumSmartTypeCompletion() {
    doParserTest(
      """
        { @Preliminary(A.B
        #) public class TimeTravel {}
          @Preliminary(a=A.B
        #) public class TimeTravel {}
          @Preliminary(a=A.B
        #, b=c) public class TimeTravel {} }""");
  }

  public void testEnumBody0() { doParserTest("{ ; }", false, true); }
  public void testEnumBody1() { doParserTest("{ RED, GREEN, BLUE; }", false, true); }
  public void testEnumBody2() { doParserTest("{ RED, GREEN, BLUE }", false, true); }
  public void testEnumBody3() { doParserTest("{ RED, GREEN, BLUE, }", false, true); }
  public void testEnumBody4() { doParserTest("{ RED(0), GREEN(1), BLUE(2); }", false, true); }
  public void testEnumBody5() { doParserTest("{ @ANNOTATION A(10) }", false, true); }
  public void testEnumBody6() { doParserTest("{ RED, GREEN, BLUE\n OurEnum() {} }", false, true); }
  public void testEnumBody7() { doParserTest("{ , ; }", false, true); }
  public void testEnumBody8() { doParserTest("{ A, }", false, true); }
  public void testEnumBody9() { doParserTest("{ , B }", false, true); }
  public void testEnumWithInitializedConstants() { doParserTest("{ A(10) { },\n B { void method() {} } }", false, true); }
  public void testEnumWithoutConstants() { doParserTest("{ private A }", false, true); }

  public void testAnnoDeclaration() { doParserTest("{ public @interface Annotation {} }"); }
  public void testAnnoSimple() { doParserTest("{ int foo (); }", true, false); }
  public void testAnnoDefault() { doParserTest("{ Class foo() default String.class; }", true, false); }
  public void testAnnoNested() { doParserTest("{ @interface Inner { String bar () default \"<unspecified>\"; } }", true, false); }
  public void testAnnoInner() { doParserTest("{ @interface Inner { double bar () default 0.0; } }"); }
  public void testAnnoOtherMembers() { doParserTest("{ int field;\n void m() {}\n class C {}\n interface I {} }", true, false); }
  public void testAnnoLoop() { doParserTest("{ @@@ int i; }"); }

  public void testTypeAnno() {
    doParserTest(
      """
        { class C<@D T extends @F Object> extends @F Object {
          @F int @F[] method() throws @F Exception {
            a = this instanceof @F C;
            C<@F @G C> c = new @Q C<@F C>();
            c = (@F Object)c;
            Class c = @TA String.class;
            @F C.field++;
          }
        } }""");
  }

  public void testReceiver() {
    doParserTest(
      "{ void m1(C this);" +
      "  void m2(T T.this);" +
      "  void m3(X Y.Z);" +
      "  T f1 = (T this) -> { };" +
      "  T f2 = (T T.this) -> { }; }");
  }

  public void testFieldSimple() { doParserTest("{ int field = 0; }"); }
  public void testFieldMulti() { doParserTest("{ int field1 = 0, field2; }"); }
  public void testUnclosedBracket() { doParserTest("{ int field[ }"); }
  public void testMissingInitializer() { doParserTest("{ int field = }"); }
  public void testUnclosedComma() { doParserTest("{ int field, }"); }
  public void testUnclosedSemicolon() { doParserTest("{ int field }"); }
  public void testMissingInitializerExpression() { doParserTest("{ int field=; }"); }
  public void testMultiLineUnclosed() { doParserTest("{ int \n Object o; }"); }
  public void testUnclosedField1() { doParserTest("{ String f1\n\n @Anno String f2; }"); }
  public void testUnclosedField2() { doParserTest("{ String f1\n\n @Anno\n String f2; }"); }

  public void testMethodNormal0() { doParserTest("{ void f() {} }"); }
  public void testMethodNormal1() { doParserTest("{ void f(); }"); }
  public void testMethodNormal2() { doParserTest("{ default public void f() { } }"); }
  public void testSemicolons() { doParserTest("{ void f() {}; void g() {}; }"); }
  public void testUnclosed0() { doParserTest("{ void f() }"); }
  public void testExtension() { doParserTest("{ default int f() throws E { return 42; } }"); }
  public void testUnclosed1() { doParserTest("{ void f( }"); }
  public void testUnclosed2() { doParserTest("{ void f()\n void g(); }"); }
  public void testUnclosed3() { doParserTest("{ void f(int a }"); }
  public void testUnclosed4() { doParserTest("{ void f(int a,, }"); }
  public void testUnclosed5() { doParserTest("{ void f(int a,); }"); }
  public void testUnclosed6() { doParserTest("{ void f() default ; }", true, false); }
  public void testUnclosed7() { doParserTest("{ void f() default {return 42;} }", true, false); }
  public void testUnclosed8() { doParserTest("{ void f() default }"); }
  public void testUnclosed9() { doParserTest("{ void f(int a; void bar(); }"); }
  public void testConstructorBrackets() { doParserTest("{ A() [] { } }"); }
  public void testVarArgBrackets() { doParserTest("{ void foo(int... x[]); }"); }

  public void testGenericMethod() {
    doParserTest(
      """
        { public static <E> test();
         <E> void test1();
         <E1 extends Integer, E2 extends Runnable> String test2(); }""");
  }

  public void testGenericMethodErrors() { doParserTest("{ <Error sss /> test <error>(); }"); }
  public void testErrors() { doParserTest("{ public static <error descr=\"2\">protected int f1 = 0; }"); }
  public void testCompletionHack0() { doParserTest("{ <X IntelliJIdeaRulezz>\n String s = \"\"; }"); }
  public void testCompletionHack1() { doParserTest("{ <X\n String s = \"\"; }"); }
  public void testCompletionHack2() { doParserTest("{ String foo() def }", true, false); }
  public void testWildcardParsing() { doParserTest("{ List<? extends B> x(Collection<? super B> x); }"); }
  public void testParameterAnnotation() { doParserTest("{ void foo (@Annotation(value=77) int param) {} }"); }
  public void testParameterizedMethod() { doParserTest("{ @Nullable <T> T bar() {} }"); }
  public void testMethodNameOmitted() { doParserTest("{ void(); }"); }

  private void doParserTest(String text) {
    doParserTest(text, false, false);
  }

  private void doParserTest(String text, boolean isAnnotation, boolean isEnum) {
    doParserTest(text, builder -> JavaParser.INSTANCE.getDeclarationParser().parseClassBodyWithBraces(builder, isAnnotation, isEnum));
  }
}