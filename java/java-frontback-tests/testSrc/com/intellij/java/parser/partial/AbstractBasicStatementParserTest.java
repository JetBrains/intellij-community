// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import com.intellij.pom.java.LanguageLevel;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicStatementParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicStatementParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/statements", configurator);
  }

  @Test
  public void testBlockSimple() { doBlockParserTest("{ {} }"); }
  @Test
  public void testBlockEmpty() { doBlockParserTest("{ ; }"); }
  @Test
  public void testAnonymousInSmartCompletion() { doBlockParserTest("{ new Foo(hash\n#) {};\n new Foo(hash\n#, bar) {};\n new Foo(hash\n#x) {}; }"); }
  @Test
  public void testBlockIncomplete0() { doBlockParserTest("{ /*}"); }
  @Test
  public void testBlockIncomplete1() { doBlockParserTest("{ { }"); }
  @Test
  public void testBlockIncomplete2() { doBlockParserTest("{ else; catch; finally; }"); }
  @Test
  public void testSCR5202() { doBlockParserTest("{ String.class.\n String[] strings; }"); }

  @Test
  public void testAssertNormal0() { doParserTest("assert cond;"); }
  @Test
  public void testAssertNormal1() { doParserTest("assert cond : message;"); }

  @Test
  public void testAssignmentSimple0() { doParserTest("int[] a;\n a[0] = 0;"); }
  @Test
  public void testAssignmentSimple1() { doParserTest("a=1; int b=2;"); }

  @Test
  public void testBreakNormal0() { doParserTest("break;"); }
  @Test
  public void testBreakNormal1() { doParserTest("break LABEL;"); }
  @Test
  public void testBreakIncomplete() { doParserTest("break"); }

  @Test
  public void testYield() { doParserTest("yield yield;"); }
  @Test
  public void testYieldNested() { doParserTest("yield switch (0) { default: yield 42; };"); }
  @Test
  public void testYieldIncomplete0() { doParserTest("yield "); }
  @Test
  public void testYieldIncomplete1() { doParserTest("yield x"); }
  @Test
  public void testYieldCall() { doParserTest("foo.yield();"); }
  @Test
  public void testYieldDecrement() { doParserTest("yield ++yield;"); }
  @Test
  public void testYieldCallNonQualified() { doParserTest("yield();"); }
  @Test
  public void testYieldCallNonQualifiedWithLambda() { doParserTest("yield(() -> {});"); }
  @Test
  public void testYieldLambda() { doParserTest("yield () -> {};"); }
  @Test
  public void testYieldAssignment() { doParserTest("yield = 10;"); }
  @Test
  public void testYieldCompatibility() { setLanguageLevel(LanguageLevel.JDK_12); doParserTest("yield(2);"); }
  @Test
  public void testYieldWithTextBlock() { doParserTest("yield \"\"\"foo\"\"\""); }
  @Test
  public void testYieldBitwiseComplement() { doParserTest("yield ~1;"); }

  @Test
  public void testContinueNormal0() { doParserTest("continue;"); }
  @Test
  public void testContinueNormal1() { doParserTest("continue LABEL;"); }

  @Test
  public void testLocalVar0() { doParserTest("List<Integer> list;"); }
  @Test
  public void testLocalVar1() { doParserTest("p.@A T<P> x;"); }
  @Test
  public void testLocalVar3() { doParserTest("var var;"); }
  @Test
  public void testLocalVar4() { doParserTest("final var x;"); }
  @Test
  public void testLocalVar5() { doParserTest("@A var x;"); }
  @Test
  public void testLocalVar6() { doParserTest("@A var"); }
  @Test
  public void testLocalVar7() { doParserTest("int"); }

  @Test
  public void testExprStatement0() { doParserTest("var"); }
  @Test
  public void testExprStatement1() { doParserTest("int."); }

  @Test
  public void testDoNormal() { doParserTest("do{}while(true);"); }
  @Test
  public void testDoIncomplete0() { doParserTest("do"); }
  @Test
  public void testDoIncomplete1() { doParserTest("do foo();"); }
  @Test
  public void testDoIncomplete2() { doParserTest("do foo(); while"); }
  @Test
  public void testDoIncomplete3() { doParserTest("do foo(); while("); }
  @Test
  public void testDoIncomplete4() { doParserTest("do foo(); while();"); }
  @Test
  public void testDoIncomplete5() { doParserTest("do foo(); while(\n g();"); }
  @Test
  public void testDoIncomplete6() { doParserTest("do foo(); while(cond)"); }

  @Test
  public void testFor() { doParserTest("for(Iterator<String> it = null; it.hasNext();) { String s = it.next(); }"); }
  @Test
  public void testForNormal0() { doParserTest("for(int i = 0; i < 10; i++)\n ;"); }
  @Test
  public void testForNormal1() { doParserTest("for( ; ; ) foo();"); }
  @Test
  public void testForNormal2() { doParserTest("for(var x = 0; ;) ;"); }
  @Test
  public void testForNormal3() { doParserTest("for(var x : list) ;"); }
  @Test
  public void testForNormal4() { doParserTest("for(foo();;) ;"); }
  @Test
  public void testForNormal5() { doParserTest("for(((foo()));;) ;"); }
  @Test
  public void testForNormal6() { doParserTest("for (getI(getS()); a; b = true)"); }
  @Test
  public void testForInitExpressionList() { doParserTest("for(foo(), bar(); i < 3; i++) ;"); }
  @Test
  public void testForIncorrect() { doParserTest("for ((Point(var x, var y)) : pointArray) ;"); } // pattern with parens can't be parsed
  @Test
  public void testForIncomplete0() { doParserTest("for"); }
  @Test
  public void testForIncomplete1() { doParserTest("for("); }
  @Test
  public void testForIncomplete2() { doParserTest("for(int i = 0;"); }
  @Test
  public void testForIncomplete3() { doParserTest("for(int i = 0; i < 10"); }
  @Test
  public void testForIncomplete4() { doParserTest("for(int i = 0; i < 10;"); }
  @Test
  public void testForIncomplete5() { doParserTest("for(int i = 0; i < 10; i++"); }
  @Test
  public void testForIncomplete6() { doParserTest("for(int i = 0; i < 10; i++)"); }
  @Test
  public void testForIncomplete7() { doParserTest("for() foo();"); }
  @Test
  public void testForIncomplete8() { doParserTest("for(int i = 0;) foo();"); }
  @Test
  public void testForIncomplete9() { doParserTest("for(int i = 0; i < 0) foo();"); }
  @Test
  public void testForIncomplete10() { doParserTest("for(var x"); }
  @Test
  public void testForInvalid0() { doParserTest("for(if (i<0) i++; ;) ;"); }
  @Test
  public void testForInvalid1() { doParserTest("for(class C { }; ;) ;"); }
  @Test
  public void testForComments0() { doParserTest("for(int i=0; i<1; i++ /**/) ;"); }
  @Test
  public void testForComments1() { doParserTest("for(int i=0; i<1; i++, j++ /**/) ;"); }

  @Test
  public void testForEach() { doParserTest("for(Object o : map.entrySet()) ;"); }
  @Test
  public void testForEach1() { doParserTest("for (Point(var x, var y) : pointArray) ;"); }
  @Test
  public void testForEachIncomplete0() { doParserTest("for(Object  : list) ;"); }
  @Test
  public void testForEachIncomplete1() { doParserTest("for (Point(var x, var y) : pointArray ;"); }
  @Test
  public void testForEachIncomplete2() { doParserTest("for (Point(var x, var y) : ) ;"); }
  @Test
  public void testForEachIncomplete3() { doParserTest("for (Point(var x, var y) ) ;"); }
  @Test
  public void testForEachIncomplete4() { doParserTest("for (Point(var x, var y) "); }
  @Test
  public void testForEachIncomplete5() { doParserTest("for (Point(var x, var y) foo.bar + 1) ;"); }

  @Test
  public void testIfNormalWithElse() { doParserTest("if (a){ f1(); } else{ f2(); }"); }
  @Test
  public void testIfNormalNoElse() { doParserTest("if (a) f1();"); }
  @Test
  public void testIfIncomplete0() { doParserTest("if"); }
  @Test
  public void testIfIncomplete1() { doParserTest("if ("); }
  @Test
  public void testIfIncomplete2() { doParserTest("if (\n foo();"); }
  @Test
  public void testIfIncomplete3() { doParserTest("if (cond"); }
  @Test
  public void testIfIncomplete4() { doParserTest("if (cond)"); }
  @Test
  public void testIfIncomplete5() { doParserTest("if () foo();"); }
  @Test
  public void testIfIncomplete6() { doParserTest("if (cond) foo(); else"); }

  @Test
  public void testLabelSimple() { doParserTest("Loop:\n while(true) ;"); }

  @Test
  public void testReturnNoResult() { doParserTest("return;"); }
  @Test
  public void testReturnWithResult() { doParserTest("return 10;"); }
  @Test
  public void testReturnIncomplete0() { doParserTest("return"); }
  @Test
  public void testReturnIncomplete1() { doParserTest("return a"); }

  @Test
  public void testSwitchNormal() { doParserTest("switch(o){}"); }
  @Test
  public void testSwitchIncomplete0() { doParserTest("switch"); }
  @Test
  public void testSwitchIncomplete1() { doParserTest("switch("); }
  @Test
  public void testSwitchIncomplete2() { doParserTest("switch(o"); }
  @Test
  public void testSwitchIncomplete3() { doParserTest("switch(o)"); }
  @Test
  public void testSwitchIncomplete4() { doParserTest("switch(){}"); }
  @Test
  public void testSwitchIncomplete5() { doParserTest("switch(\n foo();"); }

  @Test
  public void testSwitchLabelsNormal() { doParserTest("case 1: break; default: break;"); }
  @Test
  public void testSwitchLabelsMultiple() { doParserTest("case 1, 2: break;"); }
  @Test
  public void testSwitchLabelsIncomplete0() { doParserTest("case"); }
  @Test
  public void testSwitchLabelsIncomplete1() { doParserTest("case 2"); }
  @Test
  public void testSwitchLabelsIncomplete2() { doParserTest("default"); }
  @Test
  public void testSwitchLabelsIncomplete3() { doParserTest("default 3:"); }
  @Test
  public void testSwitchLabelsIncomplete4() { doParserTest("case :"); }
  @Test
  public void testSwitchLabelsIncomplete5() { doParserTest("case 1, : break;"); }

  @Test
  public void testSwitchRules0() { doParserTest("default ->"); }
  @Test
  public void testSwitchRules1() { doParserTest("default -> return;"); }
  @Test
  public void testSwitchRules2() { doParserTest("case 1 -> { }"); }
  @Test
  public void testSwitchRules3() { doParserTest("case 1 -> { };;"); }
  @Test
  public void testSwitchRules4() { doParserTest("case 1 -> throw new Exception()"); }
  @Test
  public void testSwitchRules5() { doParserTest("case 1 -> throw new Exception();"); }
  @Test
  public void testSwitchRules6() { doParserTest("case 1 -> digit = '1'"); }
  @Test
  public void testSwitchRules7() { doParserTest("case 1 -> '1';"); }
  @Test
  public void testSwitchRules8() { doParserTest("case 1 -> ;"); }
  @Test
  public void testSwitchRules9() { doParserTest("case b -> f(b);"); }
  @Test
  public void testSwitchRules10() { doParserTest("case (b) -> f(b);"); }
  @Test
  public void testSwitchRules11() { doParserTest("case 1, 2 -> { }"); }
  @Test
  public void testSwitchRules12() { doParserTest("case 1, -> { }"); }
  @Test
  public void testSwitchRules13() { doParserTest("case null, default -> { }"); }
  @Test
  public void testSwitchRules14() { doParserTest("case default -> { }"); }

  @Test
  public void testSwitchRulesWithPattern1() { doParserTest("case int[] ia -> { }"); }
  @Test
  public void testSwitchRulesWithPattern2() { doParserTest("case Integer i -> { }"); }
  @Test
  public void testSwitchRulesWithPattern3() { doParserTest("case A when when when.foo() -> {}"); }
  @Test
  public void testSwitchRulesWithPattern4() { doParserTest("case null, default -> { }"); }
  @Test
  public void testSwitchRulesWithPattern5() { doParserTest("case null -> { }"); }
  @Test
  public void testSwitchRulesWithPattern6() { doParserTest("case Integer i when true -> { }"); }
  @Test
  public void testSwitchRulesWithPattern7() { doParserTest("case R(int i) when true -> {}"); }
  @Test
  public void testSwitchRulesWithPattern8() { doParserTest("case R(int i) when a < b -> {}"); }
  @Test
  public void testSwitchRulesWithPattern9() { doParserTest("case A when when when.foo() -> {}"); }
  @Test
  public void testSwitchRulesWithPattern10() { doParserTest("case when(boolean when) when when -> {}"); }
  @Test
  public void testSwitchRulesWithPattern11() { doParserTest("case when when when when(when) -> {}"); }
  @Test
  public void testSwitchRulesWithPattern12() { doParserTest("case when when -> {}"); }
  @Test
  public void testSwitchRulesWithPatternIncomplete1() { doParserTest("case (Integer i  -> { }"); }
  @Test
  public void testSwitchRulesWithPatternIncomplete2() { doParserTest("case Integer i, -> { }"); }
  @Test
  public void testSwitchRulesWithPatternIncomplete3() { doParserTest("case Integer i when -> { }"); }

  @Test
  public void testSyncNormal() { doParserTest("synchronized(o){}"); }
  @Test
  public void testSyncIncomplete0() { doParserTest("synchronized"); }
  @Test
  public void testSyncIncomplete1() { doParserTest("synchronized("); }
  @Test
  public void testSyncIncomplete2() { doParserTest("synchronized(o"); }
  @Test
  public void testSyncIncomplete3() { doParserTest("synchronized(o)"); }
  @Test
  public void testSyncIncomplete4() { doParserTest("synchronized(){}"); }
  @Test
  public void testSyncIncomplete5() { doParserTest("synchronized(\n foo();"); }

  @Test
  public void testThrowNormal() { doParserTest("throw e;"); }
  @Test
  public void testThrowIncomplete0() { doParserTest("throw"); }
  @Test
  public void testThrowIncomplete1() { doParserTest("throw e"); }

  @Test
  public void testTryNormal0() { doParserTest("try{}catch(E e){}"); }
  @Test
  public void testTryNormal1() { doParserTest("try{}catch(final E e){}finally{}"); }
  @Test
  public void testTryNormal2() { doParserTest("try{}finally{}"); }
  @Test
  public void testTryNormal3() { doParserTest("try{}catch(A|B e){}"); }
  @Test
  public void testTryNormal4() { doParserTest("try(R r = 0){}"); }
  @Test
  public void testTryNormal5() { doParserTest("try(R1 r1 = 1; R2 r2 = 2){}"); }
  @Test
  public void testTryNormal6() { doParserTest("try(R r = 0;){}"); }
  @Test
  public void testTryNormal7() { doParserTest("try(r){}"); }
  @Test
  public void testTryNormal8() { doParserTest("try(r;){}"); }
  @Test
  public void testTryNormal9() { doParserTest("try(r1; R r2 = 0){}"); }
  @Test
  public void testTryNormal10() { doParserTest("try(this){}"); }
  @Test
  public void testTryNormal11() { doParserTest("try(new R()){}"); }
  @Test
  public void testTryNormal12() { doParserTest("try(R.create()){}"); }
  @Test
  public void testTryNormal13() { doParserTest("try(var r = null){}"); }
  @Test
  public void testTryIncomplete0() { doParserTest("try"); }
  @Test
  public void testTryIncomplete1() { doParserTest("try{}"); }
  @Test
  public void testTryIncomplete2() { doParserTest("try{}catch"); }
  @Test
  public void testTryIncomplete3() { doParserTest("try{}catch("); }
  @Test
  public void testTryIncomplete4() { doParserTest("try{}catch(E"); }
  @Test
  public void testTryIncomplete5() { doParserTest("try{}catch(E e"); }
  @Test
  public void testTryIncomplete6() { doParserTest("try{}catch(E e)"); }
  @Test
  public void testTryIncomplete7() { doParserTest("try{}finally"); }
  @Test
  public void testTryIncomplete8() { doParserTest("try{}catch(A|)"); }
  @Test
  public void testTryIncomplete9() { doParserTest("try{}catch(A|B)"); }
  @Test
  public void testTryIncomplete10() { doParserTest("try({}"); }
  @Test
  public void testTryIncomplete11() { doParserTest("try(){}"); }
  @Test
  public void testTryIncomplete12() { doParserTest("try(;){}"); }
  @Test
  public void testTryIncomplete13() { doParserTest("try(final ){}"); }
  @Test
  public void testTryIncomplete14() { doParserTest("try(int){}"); }
  @Test
  public void testTryIncomplete15() { doParserTest("try(R r){}"); }
  @Test
  public void testTryIncomplete16() { doParserTest("try(R r =){}"); }
  @Test
  public void testTryIncomplete17() { doParserTest("try(R r = 0;;){}"); }
  @Test
  public void testTryIncomplete18() { doParserTest("try(R<T> r){}"); }
  @Test
  public void testTryIncomplete19() { doParserTest("try(var r){}"); }

  @Test
  public void testWhileNormal() { doParserTest("while (true) foo();"); }
  @Test
  public void testWhileIncomplete0() { doParserTest("while"); }
  @Test
  public void testWhileIncomplete1() { doParserTest("while ("); }
  @Test
  public void testWhileIncomplete2() { doParserTest("while(\n foo();"); }
  @Test
  public void testWhileIncomplete3() { doParserTest("while(cond"); }
  @Test
  public void testWhileIncomplete4() { doParserTest("while(cond)"); }
  @Test
  public void testWhileIncomplete5() { doParserTest("while() foo();"); }

  @Test
  public void testExprStmtStarts() { doParserTest("col < allCells[0]"); }

  @Test
  public void testConstructorRef() { doParserTest("Foo::new"); }
  @Test
  public void testConstructorWithTypeParamsRef() { doParserTest("Foo<Integer>::new"); }

  @Test
  public void testVarLabel() { doParserTest("var: foo();"); }

  protected abstract void doBlockParserTest(String text);

  protected abstract void doParserTest(String text);
}