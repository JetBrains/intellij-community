/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.parser.partial;

import com.intellij.java.parser.JavaParsingTestCase;
import com.intellij.lang.java.parser.JavaParser;

public class StatementParserTest extends JavaParsingTestCase {
  public StatementParserTest() {
    super("parser-partial/statements");
  }

  public void testBlockSimple() { doBlockParserTest("{ {} }"); }
  public void testBlockEmpty() { doBlockParserTest("{ ; }"); }
  public void testAnonymousInSmartCompletion() { doBlockParserTest("{ new Foo(hash\n#) {};\n new Foo(hash\n#, bar) {};\n new Foo(hash\n#x) {}; }"); }
  public void testBlockIncomplete0() { doBlockParserTest("{ /*}"); }
  public void testBlockIncomplete1() { doBlockParserTest("{ { }"); }
  public void testBlockIncomplete2() { doBlockParserTest("{ else; catch; finally; }"); }
  public void testSCR5202() { doBlockParserTest("{ String.class.\n String[] strings; }"); }

  public void testAssertNormal0() { doParserTest("assert cond;"); }
  public void testAssertNormal1() { doParserTest("assert cond : message;"); }

  public void testAssignmentSimple0() { doParserTest("int[] a;\n a[0] = 0;"); }
  public void testAssignmentSimple1() { doParserTest("a=1; int b=2;"); }

  public void testBreakNormal0() { doParserTest("break;"); }
  public void testBreakNormal1() { doParserTest("break LABEL;"); }

  public void testContinueNormal0() { doParserTest("continue;"); }
  public void testContinueNormal1() { doParserTest("continue LABEL;"); }

  public void testLocalVar0() { doParserTest("List<Integer> list;"); }
  public void testLocalVar1() { doParserTest("p.@A T<P> x;"); }
  public void testLocalVar3() { doParserTest("var var;"); }
  public void testLocalVar4() { doParserTest("final var x;"); }
  public void testLocalVar5() { doParserTest("@A var x;"); }
  public void testLocalVar6() { doParserTest("@A var"); }
  public void testLocalVar7() { doParserTest("int"); }

  public void testExprStatement0() { doParserTest("var"); }
  public void testExprStatement1() { doParserTest("int."); }

  public void testDoNormal() { doParserTest("do{}while(true);"); }
  public void testDoIncomplete0() { doParserTest("do"); }
  public void testDoIncomplete1() { doParserTest("do foo();"); }
  public void testDoIncomplete2() { doParserTest("do foo(); while"); }
  public void testDoIncomplete3() { doParserTest("do foo(); while("); }
  public void testDoIncomplete4() { doParserTest("do foo(); while();"); }
  public void testDoIncomplete5() { doParserTest("do foo(); while(\n g();"); }
  public void testDoIncomplete6() { doParserTest("do foo(); while(cond)"); }

  public void testFor() { doParserTest("for(Iterator<String> it = null; it.hasNext();) { String s = it.next(); }"); }
  public void testForNormal0() { doParserTest("for(int i = 0; i < 10; i++)\n ;"); }
  public void testForNormal1() { doParserTest("for( ; ; ) foo();"); }
  public void testForNormal2() { doParserTest("for(var x = 0; ;) ;"); }
  public void testForNormal3() { doParserTest("for(var x : list) ;"); }
  public void testForIncomplete0() { doParserTest("for"); }
  public void testForIncomplete1() { doParserTest("for("); }
  public void testForIncomplete2() { doParserTest("for(int i = 0;"); }
  public void testForIncomplete3() { doParserTest("for(int i = 0; i < 10"); }
  public void testForIncomplete4() { doParserTest("for(int i = 0; i < 10;"); }
  public void testForIncomplete5() { doParserTest("for(int i = 0; i < 10; i++"); }
  public void testForIncomplete6() { doParserTest("for(int i = 0; i < 10; i++)"); }
  public void testForIncomplete7() { doParserTest("for() foo();"); }
  public void testForIncomplete8() { doParserTest("for(int i = 0;) foo();"); }
  public void testForIncomplete9() { doParserTest("for(int i = 0; i < 0) foo();"); }
  public void testForIncomplete10() { doParserTest("for(var x"); }
  public void testForInvalid0() { doParserTest("for(if (i<0) i++; ;) ;"); }
  public void testForInvalid1() { doParserTest("for(class C { }; ;) ;"); }
  public void testForComments0() { doParserTest("for(int i=0; i<1; i++ /**/) ;"); }
  public void testForComments1() { doParserTest("for(int i=0; i<1; i++, j++ /**/) ;"); }

  public void testForEach() { doParserTest("for(Object o : map.entrySet()) ;"); }
  public void testForEachIncomplete0() { doParserTest("for(Object  : list) ;"); }

  public void testIfNormalWithElse() { doParserTest("if (a){ f1(); } else{ f2(); }"); }
  public void testIfNormalNoElse() { doParserTest("if (a) f1();"); }
  public void testIfIncomplete0() { doParserTest("if"); }
  public void testIfIncomplete1() { doParserTest("if ("); }
  public void testIfIncomplete2() { doParserTest("if (\n foo();"); }
  public void testIfIncomplete3() { doParserTest("if (cond"); }
  public void testIfIncomplete4() { doParserTest("if (cond)"); }
  public void testIfIncomplete5() { doParserTest("if () foo();"); }
  public void testIfIncomplete6() { doParserTest("if (cond) foo(); else"); }

  public void testLabelSimple() { doParserTest("Loop:\n while(true) ;"); }

  public void testReturnNoResult() { doParserTest("return;"); }
  public void testReturnWithResult() { doParserTest("return 10;"); }
  public void testReturnIncomplete0() { doParserTest("return"); }
  public void testReturnIncomplete1() { doParserTest("return a"); }

  public void testSwitchNormal() { doParserTest("switch(o){}"); }
  public void testSwitchIncomplete0() { doParserTest("switch"); }
  public void testSwitchIncomplete1() { doParserTest("switch("); }
  public void testSwitchIncomplete2() { doParserTest("switch(o"); }
  public void testSwitchIncomplete3() { doParserTest("switch(o)"); }
  public void testSwitchIncomplete4() { doParserTest("switch(){}"); }
  public void testSwitchIncomplete5() { doParserTest("switch(\n foo();"); }

  public void testSwitchLabelsNormal() { doParserTest("case 1: break; default: break;"); }
  public void testSwitchLabelsIncomplete0() { doParserTest("case"); }
  public void testSwitchLabelsIncomplete1() { doParserTest("case 2"); }
  public void testSwitchLabelsIncomplete2() { doParserTest("default"); }
  public void testSwitchLabelsIncomplete3() { doParserTest("default 3:"); }

  public void testSyncNormal() { doParserTest("synchronized(o){}"); }
  public void testSyncIncomplete0() { doParserTest("synchronized"); }
  public void testSyncIncomplete1() { doParserTest("synchronized("); }
  public void testSyncIncomplete2() { doParserTest("synchronized(o"); }
  public void testSyncIncomplete3() { doParserTest("synchronized(o)"); }
  public void testSyncIncomplete4() { doParserTest("synchronized(){}"); }
  public void testSyncIncomplete5() { doParserTest("synchronized(\n foo();"); }

  public void testThrowNormal() { doParserTest("throw e;"); }
  public void testThrowIncomplete0() { doParserTest("throw"); }
  public void testThrowIncomplete1() { doParserTest("throw e"); }

  public void testTryNormal0() { doParserTest("try{}catch(E e){}"); }
  public void testTryNormal1() { doParserTest("try{}catch(final E e){}finally{}"); }
  public void testTryNormal2() { doParserTest("try{}finally{}"); }
  public void testTryNormal3() { doParserTest("try{}catch(A|B e){}"); }
  public void testTryNormal4() { doParserTest("try(R r = 0){}"); }
  public void testTryNormal5() { doParserTest("try(R1 r1 = 1; R2 r2 = 2){}"); }
  public void testTryNormal6() { doParserTest("try(R r = 0;){}"); }
  public void testTryNormal7() { doParserTest("try(r){}"); }
  public void testTryNormal8() { doParserTest("try(r;){}"); }
  public void testTryNormal9() { doParserTest("try(r1; R r2 = 0){}"); }
  public void testTryNormal10() { doParserTest("try(this){}"); }
  public void testTryNormal11() { doParserTest("try(new R()){}"); }
  public void testTryNormal12() { doParserTest("try(R.create()){}"); }
  public void testTryNormal13() { doParserTest("try(var r = null){}"); }
  public void testTryIncomplete0() { doParserTest("try"); }
  public void testTryIncomplete1() { doParserTest("try{}"); }
  public void testTryIncomplete2() { doParserTest("try{}catch"); }
  public void testTryIncomplete3() { doParserTest("try{}catch("); }
  public void testTryIncomplete4() { doParserTest("try{}catch(E"); }
  public void testTryIncomplete5() { doParserTest("try{}catch(E e"); }
  public void testTryIncomplete6() { doParserTest("try{}catch(E e)"); }
  public void testTryIncomplete7() { doParserTest("try{}finally"); }
  public void testTryIncomplete8() { doParserTest("try{}catch(A|)"); }
  public void testTryIncomplete9() { doParserTest("try{}catch(A|B)"); }
  public void testTryIncomplete10() { doParserTest("try({}"); }
  public void testTryIncomplete11() { doParserTest("try(){}"); }
  public void testTryIncomplete12() { doParserTest("try(;){}"); }
  public void testTryIncomplete13() { doParserTest("try(final ){}"); }
  public void testTryIncomplete14() { doParserTest("try(int){}"); }
  public void testTryIncomplete15() { doParserTest("try(R r){}"); }
  public void testTryIncomplete16() { doParserTest("try(R r =){}"); }
  public void testTryIncomplete17() { doParserTest("try(R r = 0;;){}"); }
  public void testTryIncomplete18() { doParserTest("try(R<T> r){}"); }
  public void testTryIncomplete19() { doParserTest("try(var r){}"); }

  public void testWhileNormal() { doParserTest("while (true) foo();"); }
  public void testWhileIncomplete0() { doParserTest("while"); }
  public void testWhileIncomplete1() { doParserTest("while ("); }
  public void testWhileIncomplete2() { doParserTest("while(\n foo();"); }
  public void testWhileIncomplete3() { doParserTest("while(cond"); }
  public void testWhileIncomplete4() { doParserTest("while(cond)"); }
  public void testWhileIncomplete5() { doParserTest("while() foo();"); }

  private void doBlockParserTest(String text) {
    doParserTest(text, builder -> JavaParser.INSTANCE.getStatementParser().parseCodeBlockDeep(builder, true));
  }

  private void doParserTest(String text) {
    doParserTest(text, builder -> JavaParser.INSTANCE.getStatementParser().parseStatements(builder));
  }
}