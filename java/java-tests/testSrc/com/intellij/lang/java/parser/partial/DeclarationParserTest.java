/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.java.parser.partial;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.DeclarationParser;
import com.intellij.lang.java.parser.JavaParsingTestCase;
import com.intellij.pom.java.LanguageLevel;


// todo: fix parser and uncomment tests
public class DeclarationParserTest extends JavaParsingTestCase {
  public DeclarationParserTest() {
    super("parser-partial/declarations");
  }

  public void testEmptyBody0() { doParserTest("{ }", false, false); }
  public void testEmptyBody1() { doParserTest("{ ", false, false); }
  public void testEmptyBody2() { doParserTest("{ @Null }", false, false); }

  public void testNoType() { doParserTest("{ new X(); }", false, false); }
  public void testExtraSemicolon() { doParserTest("{ class C { }; }", false, false); }
  public void testParameterizedClass() { doParserTest("{ public class A <T extends java.util.List> { } }", false, false); }
  public void testPines() { doParserTest("{ class A<T extends List<String>> extends List<List<Integer>> { } }", false, false); }
  public void testIncompleteAnnotation() { doParserTest("{ public class Foo { public void testSomething(); @Null } }", false, false); }
  public void testClassInit() { doParserTest("{ { /*comment*/ } }", false, false); }
  public void testAnnoDeclaration() { doParserTest("{ public @interface Annotation {} }", false, false); }
  public void testEnumSmartTypeCompletion() { doParserTest("{ @Preliminary(A.B\n#) public class TimeTravel {}\n" +
                                                           "  @Preliminary(a=A.B\n#) public class TimeTravel {}\n" +
                                                           "  @Preliminary(a=A.B\n#, b=c) public class TimeTravel {} }", false, false); }
  public void testTypeAnno() {
    withLevel(LanguageLevel.JDK_1_7,
              new Runnable() { public void run() {
                  doParserTest("{ class C<@D T extends @F Object> extends @F Object {\n" +
                                                "  @F int @F[] method() @F throws @F Exception {\n" +
                                                "    a = this instanceof @F C;\n" +
                                                "    C<@F @G C> c = new @Q C<@F C>();\n" +
                                                "    c = (@F Object)c;\n" +
                                                "    Class c = @TA String.class;\n" +
                                                "    @F C.field++;\n" +
                                                "  }\n} }", false, false);
              } });
  }

  public void testEnumBody0() { doParserTest("{ ; }", false, true); }
  public void testEnumBody1() { doParserTest("{ RED, GREEN, BLUE; }", false, true); }
  public void testEnumBody2() { doParserTest("{ RED, GREEN, BLUE }", false, true); }
  public void testEnumBody3() { doParserTest("{ RED, GREEN, BLUE, }", false, true); }
  public void testEnumBody4() { doParserTest("{ RED(0), GREEN(1), BLUE(2); }", false, true); }
  public void testEnumBody5() { doParserTest("{ @ANNOTATION A(10) }", false, true); }
  public void testEnumBody6() { doParserTest("{ RED, GREEN, BLUE\n OurEnum() {} }", false, true); }
  public void testEnumWithInitializedConstants() { doParserTest("{ A(10) { },\n B { void method() {} } }", false, true); }
  public void testEnumWithoutConstants() { doParserTest("{ private A }", false, true); }

  public void testAnnoSimple() { doParserTest("{ int foo (); }", true, false); }
  public void testAnnoDefault() { doParserTest("{ Class foo () default String.class; }", true, false); }
  public void testAnnoNested() { doParserTest("{ @interface Inner { String bar () default \"<unspecified>\"; } }", true, false); }
  public void testAnnoInner() { doParserTest("{ @interface Inner { double bar () default 0.0; } }", false, false); }
  public void testAnnoOtherMembers() { doParserTest("{ int field;\n void m() {}\n class C {}\n interface I {} }", true, false); }

  public void testFieldSimple() { doParserTest("{ int field = 0; }", false, false); }
  public void testFieldMulti() { doParserTest("{ int field1 = 0, field2; }", false, false); }
  public void testUnclosedBracket() { doParserTest("{ int field[ }", false, false); }
  public void testMissingInitializer() { doParserTest("{ int field = }", false, false); }
  public void testUnclosedComma() { doParserTest("{ int field, }", false, false); }
  public void testUnclosedSemicolon() { doParserTest("{ int field }", false, false); }
  public void testMissingInitializerExpression() { doParserTest("{ int field=; }", false, false); }
  //public void testMultiLineUnclosed() { doParserTest("{ int \n  Object o; }", false, false); }

  public void testMethodNormal0() { doParserTest("{ void f() {} }", false, false); }
  public void testMethodNormal1() { doParserTest("{ void f(); }", false, false); }
  public void testSemicolons() { doParserTest("{ void f() {}; void g() {}; }", false, false); }
  public void testUnclosed0() { doParserTest("{ void f() }", false, false); }
  public void testUnclosed1() { doParserTest("{ void f( }", false, false); }
  public void testUnclosed2() { doParserTest("{ void f()\n void g(); }", false, false); }
  public void testUnclosed3() { doParserTest("{ void f(int a }", false, false); }
  public void testUnclosed4() { doParserTest("{ void f(int a,, }", false, false); }
  public void testUnclosed5() { doParserTest("{ void f(int a,); }", false, false); }
  public void testGenericMethod() { doParserTest("{ public static <E> test();\n" +
                                                 " <E> void test1();\n" +
                                                 " <E1 extends Integer, E2 extends Runnable> String test2(); }", false, false); }
  public void testGenericMethodErrors() { doParserTest("{ <Error sss /> test <error>(); }", false, false); }
  public void testErrors() { doParserTest("{ public static <error descr=\"2\">protected int f1 = 0; }", false, false); }
  public void testCompletionHack0() { doParserTest("{ <X IntelliJIdeaRulezz>\n String s = \"\"; }", false, false); }
  public void testCompletionHack1() { doParserTest("{ <X\n String s = \"\"; }", false, false); }
  public void testCompletionHack2() { doParserTest("{ String foo() def }", true, false); }
  public void testWildcardParsing() { doParserTest("{ List<? extends B> x(Collection<? super B> x); }", false, false); }
  public void testParameterAnnotation() { doParserTest("{ void foo (@Annotation(value=77) int param) {} }", false, false); }
  public void testParameterizedMethod() { doParserTest("{ @Nullable <T> T bar() {} }", false, false); }

  private void doParserTest(final String text, final boolean isAnnotation, final boolean isEnum) {
    doParserTest(text, new TestParser() {
      public void parse(final PsiBuilder builder) {
        DeclarationParser.parseClassBodyWithBraces(builder, isAnnotation, isEnum);
      }
    });
  }
}
