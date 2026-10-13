// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicDeclarationParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicDeclarationParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/declarations", configurator);
  }

  @Test
  public void testEmptyBody0() { doParserTest("{ }"); }
  @Test
  public void testEmptyBody1() { doParserTest("{ "); }
  @Test
  public void testEmptyBody2() { doParserTest("{ @Null }"); }

  @Test
  public void testNoType() { doParserTest("{ new X(); }"); }
  @Test
  public void testExtraSemicolon() { doParserTest("{ class C { }; }"); }
  @Test
  public void testParameterizedClass() { doParserTest("{ public class A <T extends java.util.List> { } }"); }
  @Test
  public void testPines() { doParserTest("{ class A<T extends List<String>> extends List<List<Integer>> { } }"); }
  @Test
  public void testIncompleteAnnotation() { doParserTest("{ public class Foo { public void testSomething(); @Null } }"); }
  @Test
  public void testClassInit() { doParserTest("{ { /*comment*/ } }"); }

  @Test
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

  @Test
  public void testEnumBody0() { doParserTest("{ ; }", false, true); }
  @Test
  public void testEnumBody1() { doParserTest("{ RED, GREEN, BLUE; }", false, true); }
  @Test
  public void testEnumBody2() { doParserTest("{ RED, GREEN, BLUE }", false, true); }
  @Test
  public void testEnumBody3() { doParserTest("{ RED, GREEN, BLUE, }", false, true); }
  @Test
  public void testEnumBody4() { doParserTest("{ RED(0), GREEN(1), BLUE(2); }", false, true); }
  @Test
  public void testEnumBody5() { doParserTest("{ @ANNOTATION A(10) }", false, true); }
  @Test
  public void testEnumBody6() { doParserTest("{ RED, GREEN, BLUE\n OurEnum() {} }", false, true); }
  @Test
  public void testEnumBody7() { doParserTest("{ , ; }", false, true); }
  @Test
  public void testEnumBody8() { doParserTest("{ A, }", false, true); }
  @Test
  public void testEnumBody9() { doParserTest("{ , B }", false, true); }
  @Test
  public void testEnumWithInitializedConstants() { doParserTest("{ A(10) { },\n B { void method() {} } }", false, true); }
  @Test
  public void testEnumWithoutConstants() { doParserTest("{ private A }", false, true); }

  @Test
  public void testAnnoDeclaration() { doParserTest("{ public @interface Annotation {} }"); }
  @Test
  public void testAnnoSimple() { doParserTest("{ int foo (); }", true, false); }
  @Test
  public void testAnnoDefault() { doParserTest("{ Class foo() default String.class; }", true, false); }
  @Test
  public void testAnnoNested() { doParserTest("{ @interface Inner { String bar () default \"<unspecified>\"; } }", true, false); }
  @Test
  public void testAnnoInner() { doParserTest("{ @interface Inner { double bar () default 0.0; } }"); }
  @Test
  public void testAnnoOtherMembers() { doParserTest("{ int field;\n void m() {}\n class C {}\n interface I {} }", true, false); }
  @Test
  public void testAnnoLoop() { doParserTest("{ @@@ int i; }"); }

  @Test
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

  @Test
  public void testReceiver() {
    doParserTest(
      "{ void m1(C this);" +
      "  void m2(T T.this);" +
      "  void m3(X Y.Z);" +
      "  T f1 = (T this) -> { };" +
      "  T f2 = (T T.this) -> { }; }");
  }

  @Test
  public void testFieldSimple() { doParserTest("{ int field = 0; }"); }
  @Test
  public void testFieldMulti() { doParserTest("{ int field1 = 0, field2; }"); }
  @Test
  public void testUnclosedBracket() { doParserTest("{ int field[ }"); }
  @Test
  public void testMissingInitializer() { doParserTest("{ int field = }"); }
  @Test
  public void testUnclosedComma() { doParserTest("{ int field, }"); }
  @Test
  public void testUnclosedSemicolon() { doParserTest("{ int field }"); }
  @Test
  public void testMissingInitializerExpression() { doParserTest("{ int field=; }"); }
  @Test
  public void testMultiLineUnclosed() { doParserTest("{ int \n Object o; }"); }
  @Test
  public void testUnclosedField1() { doParserTest("{ String f1\n\n @Anno String f2; }"); }
  @Test
  public void testUnclosedField2() { doParserTest("{ String f1\n\n @Anno\n String f2; }"); }

  @Test
  public void testMethodNormal0() { doParserTest("{ void f() {} }"); }
  @Test
  public void testMethodNormal1() { doParserTest("{ void f(); }"); }
  @Test
  public void testMethodNormal2() { doParserTest("{ default public void f() { } }"); }
  @Test
  public void testSemicolons() { doParserTest("{ void f() {}; void g() {}; }"); }
  @Test
  public void testUnclosed0() { doParserTest("{ void f() }"); }
  @Test
  public void testExtension() { doParserTest("{ default int f() throws E { return 42; } }"); }
  @Test
  public void testUnclosed1() { doParserTest("{ void f( }"); }
  @Test
  public void testUnclosed2() { doParserTest("{ void f()\n void g(); }"); }
  @Test
  public void testUnclosed3() { doParserTest("{ void f(int a }"); }
  @Test
  public void testUnclosed4() { doParserTest("{ void f(int a,, }"); }
  @Test
  public void testUnclosed5() { doParserTest("{ void f(int a,); }"); }
  @Test
  public void testUnclosed6() { doParserTest("{ void f() default ; }", true, false); }
  @Test
  public void testUnclosed7() { doParserTest("{ void f() default {return 42;} }", true, false); }
  @Test
  public void testUnclosed8() { doParserTest("{ void f() default }"); }
  @Test
  public void testUnclosed9() { doParserTest("{ void f(int a; void bar(); }"); }
  @Test
  public void testConstructorBrackets() { doParserTest("{ A() [] { } }"); }
  @Test
  public void testVarArgBrackets() { doParserTest("{ void foo(int... x[]); }"); }

  @Test
  public void testGenericMethod() {
    doParserTest(
      """
        { public static <E> test();
         <E> void test1();
         <E1 extends Integer, E2 extends Runnable> String test2(); }""");
  }

  @Test
  public void testGenericMethodErrors() { doParserTest("{ <Error sss /> test <error>(); }"); }
  @Test
  public void testErrors() { doParserTest("{ public static <error descr=\"2\">protected int f1 = 0; }"); }
  @Test
  public void testCompletionHack0() { doParserTest("{ <X IntelliJIdeaRulezz>\n String s = \"\"; }"); }
  @Test
  public void testCompletionHack1() { doParserTest("{ <X\n String s = \"\"; }"); }
  @Test
  public void testCompletionHack2() { doParserTest("{ String foo() def }", true, false); }
  @Test
  public void testWildcardParsing() { doParserTest("{ List<? extends B> x(Collection<? super B> x); }"); }
  @Test
  public void testParameterAnnotation() { doParserTest("{ void foo (@Annotation(value=77) int param) {} }"); }
  @Test
  public void testParameterizedMethod() { doParserTest("{ @Nullable <T> T bar() {} }"); }
  @Test
  public void testMethodNameOmitted() { doParserTest("{ void(); }"); }

  private void doParserTest(String text) {
    doParserTest(text, false, false);
  }

  protected abstract void doParserTest(String text, boolean isAnnotation, boolean isEnum);
}