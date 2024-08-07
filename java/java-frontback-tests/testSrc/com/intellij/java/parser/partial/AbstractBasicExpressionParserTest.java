// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractBasicExpressionParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicExpressionParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/expressions", configurator);
  }

  public void testAssignment0() { doParserTest("a = 0"); }
  public void testAssignment1() { doParserTest("a ="); }

  public void testBinary0() { doParserTest("a + b"); }
  public void testBinary1() { doParserTest("a < b"); }
  public void testBinary2() { doParserTest("a > = b"); }
  public void testBinary3() { doParserTest("a >/**/= b"); }
  public void testBinary4() { doParserTest("a**"); }

  public void testCond0() { doParserTest("cond ? true : false"); }
  public void testCond1() { doParserTest("cond ?"); }
  public void testCond2() { doParserTest("cond ? true"); }
  public void testCond3() { doParserTest("cond ? true :"); }
  public void testCond4() { doParserTest("cond ? true : false ? true : false"); }

  public void testCondOr0() { doParserTest("a || b || c"); }
  public void testCondOr1() { doParserTest("a ||"); }

  public void testOr0() { doParserTest("a | b | c"); }
  public void testOr1() { doParserTest("a |"); }

  public void testInstanceOf0() { doParserTest("a instanceof String"); }
  public void testInstanceOf1() { doParserTest("a instanceof"); }

  public void testInstanceOfPattern0() { doParserTest("x instanceof Foo v"); }
  public void testInstanceOfPattern1() { doParserTest("x instanceof final Foo v"); }
  public void testInstanceOfPattern2() { doParserTest("x instanceof @Ann() final Foo v"); }
  public void testInstanceOfPattern3() { doParserTest("x instanceof (Foo v)"); }
  public void testInstanceOfPattern4() { doParserTest("x instanceof Foo v && v > 10"); }

  public void testNot0() { doParserTest("!!a"); }
  public void testNot1() { doParserTest("!"); }

  public void testCast0() { doParserTest("(Type)var"); }
  public void testCast1() { doParserTest("(double)1 / 5"); }

  public void testParenth0() { doParserTest("(c)"); }
  public void testParenth1() { doParserTest("(this).f--"); }
  public void testParenth2() { doParserTest("("); }
  public void testParenth3() { doParserTest("(a < b)"); }

  public void testNewInExprList() { doParserTest("call(new)"); }

  public void testNew0() { doParserTest("new A()"); }
  public void testNew1() { doParserTest("new A[1][]"); }
  public void testNew2() { doParserTest("new A[]"); }
  public void testNew3() { doParserTest("new A[][1]"); }
  public void testNew4() { doParserTest("new A[][]{null}"); }
  public void testNew5() { doParserTest("new A[1]{null}"); }
  public void testNew6() { doParserTest("new"); }
  public void testNew7() { doParserTest("new A"); }
  public void testNew8() { doParserTest("new A["); }
  public void testNew9() { doParserTest("new int"); }
  public void testNew10() { doParserTest("new int()"); }
  public void testNew11() { doParserTest("new String[0"); }
  public void testNew12() { doParserTest("new int[1][2]"); }
  public void testNew13() { doParserTest("new int[1][][2]"); }
  public void testNew14() { doParserTest("Q.new A()"); }
  public void testNew15() { doParserTest("new C<?>.B()"); }
  public void testNew16() { doParserTest("new C<>()"); }
  public void testNew17() { doParserTest("new Map<String, >()"); }
  public void testNew18() { doParserTest("new int @A [2] @B"); }
  public void testNew19() { doParserTest("new A(f; o)"); }

  public void testExprList0() { doParserTest("f(1,2)"); }
  public void testExprList1() { doParserTest("f("); }
  public void testExprList2() { doParserTest("f(1"); }
  public void testExprList3() { doParserTest("f(1,"); }
  public void testExprList4() { doParserTest("f(1,)"); }
  public void testExprList5() { doParserTest("f(1,,2)"); }
  public void testExprList6() { doParserTest("f(,2)"); }

  public void testArrayInitializer0() { doParserTest("{ }"); }
  public void testArrayInitializer1() { doParserTest("{ 1 }"); }
  public void testArrayInitializer2() { doParserTest("{ 1, }"); }
  public void testArrayInitializer3() { doParserTest("{ 1,2 }"); }
  public void testArrayInitializer4() { doParserTest("{ 1 2 }"); }
  public void testArrayInitializer5() { doParserTest("{ { }"); }
  public void testArrayInitializer6() { doParserTest("{  ,  }"); }
  public void testArrayInitializer7() { doParserTest("{  ,  , 7 }"); }
  public void testArrayInitializer8() { doParserTest("{ 8,  ,  , }"); }
  public void testArrayInitializer9() { doParserTest("{  , 9 }"); }

  public void testPinesInReferenceExpression0() { doParserTest("Collections.<String>sort(null)"); }
  public void testPinesInReferenceExpression1() { doParserTest("this.<String>sort(null)"); }
  public void testPinesInReferenceExpression2() { doParserTest("<String>super(null)"); }

  public void testGE0() { doParserTest("x >>>= 8 >> 2"); }
  public void testGE1() { doParserTest("x >= 2"); }

  public void testIncompleteCast() { doParserTest("f((ArrayList<String>) )"); }

  public void testShiftRight() { doParserTest("x >>= 2"); }

  public void testCorruptAnnoInCast() { doParserTest("(@&"); }

  public void testIllegalWildcard() { doParserTest("this.<?>foo()"); }

  public void testIllegalBound() { doParserTest("C.<T extends S>foo()"); }

  public void testQualifiedSuperMethodCall0() { doParserTest("new D().super(0)"); }
  public void testQualifiedSuperMethodCall1() { doParserTest("d.super(0)"); }
  public void testQualifiedSuperMethodCall2() { doParserTest("(new O()).<T>super()"); }
  public void testQualifiedSuperMethodCall3() { doParserTest("C.A.super()"); }

  public void testSuperMethodCallTypeParameterList() { doParserTest("super()"); }

  public void testPrimitiveClassObjectAccess() { doParserTest("int.class"); }

  public void testPrimitiveFieldAccess() { doParserTest("int.x"); }

  public void testChainedClassObjectAccess() { doParserTest("A.class.B.class"); }
  public void testChainedThisObjectAccess() { doParserTest("A.this.B.this"); }

  public void testAnnotatedRefExpr0() { doParserTest("@A C1.@B() C2"); }
  public void testAnnotatedRefExpr1() { doParserTest("@A C1.@B() ()"); }

  public void testMethodRef0() { doParserTest("a.b.C::m"); }
  public void testMethodRef1() { doParserTest("a.b.C<T>::new"); }
  public void testMethodRef2() { doParserTest("C::<T>m"); }
  public void testMethodRef3() { doParserTest("a[i]::m"); }
  public void testMethodRef4() { doParserTest("int[]::clone"); }
  public void testMethodRef5() { doParserTest("(f ? list.map(String::length) : Collections.emptyList())::iterator"); }

  public void testLambdaExpression0() { doParserTest("p -> 42"); }
  public void testLambdaExpression1() { doParserTest("p -> "); }
  public void testLambdaExpression2() { doParserTest("p -> {"); }
  public void testLambdaExpression3() { doParserTest("(p) -> { }"); }
  public void testLambdaExpression4() { doParserTest("(p, v) -> null"); }
  public void testLambdaExpression5() { doParserTest("(p, v -> null"); }
  public void testLambdaExpression6() { doParserTest("(p, v, -> null"); }
  public void testLambdaExpression7() { doParserTest("(p -> null)"); }
  public void testLambdaExpression8() { doParserTest("(I)(p) -> null"); }
  public void testLambdaExpression9() { doParserTest("(I)p -> null"); }
  public void testLambdaExpression10() { doParserTest("(I)(p -> null)"); }
  public void testLambdaExpression11() { doParserTest("() -> { }"); }
  public void testLambdaExpression12() { doParserTest("(I1 & I2) () -> null"); }
  public void testLambdaExpression13() { doParserTest("(I1 & I2) () -> {}"); }
  public void testLambdaExpression14() { doParserTest("(String t) -> t"); }
  public void testLambdaExpression15() { doParserTest("(int a, int b) -> a + b"); }
  public void testLambdaExpression16() { doParserTest("(final int x) -> x"); }
  public void testLambdaExpression17() { doParserTest("(String s -> s"); }
  public void testLambdaExpression18() { doParserTest("(java.lang.String s, -> s"); }
  public void testLambdaExpression19() { doParserTest("(@A T t) -> (null)"); }
  public void testLambdaExpression20() { doParserTest("(@A T) -> (null)"); }
  public void testLambdaExpression21() { doParserTest("(T @A() [] x) -> { }"); }
  public void testLambdaExpression22() { doParserTest("(T x @A() []) -> { }"); }
  public void testLambdaExpression23() { doParserTest("(T... t) -> t.length"); }
  public void testLambdaExpression24() { doParserTest("var -> var"); }
  public void testLambdaExpression25() { doParserTest("(var) -> var"); }
  public void testLambdaExpression26() { doParserTest("(var var) -> var"); }
  public void testLambdaExpression27() { doParserTest("z > (w) -> v"); }

  public void testTextBlockLiteral0() { doParserTest("\"\"\".\"\"\""); }

  public void testSwitch0() { doParserTest("switch (i) { case 1 -> 1; default -> 2; }"); }

  public void testYieldAsExpr0() { doParserTest("yield.run()"); }
  public void testYieldAsExpr1() { doParserTest("yield++"); }
  public void testYieldAsExpr2() { doParserTest("yield += 2"); }
  public void testYieldAsExpr3() { doParserTest("yield ? 10 : 20"); }

  public void testStringTemplate1() { doParserTest(); }
  public void testStringTemplate2() { doParserTest(); }
  public void testStringTemplate3() { doParserTest(); }
  public void testStringTemplate4() { doParserTest(); }
  public void testStringTemplate5() { doParserTest(); }
  public void testStringTemplate6() { doParserTest(); }
  public void testStringTemplate7() { doParserTest(); }
  public void testStringTemplate8() { doParserTest(); }

  public void testTextBlockTemplate1() { doParserTest(); }
  public void testTextBlockTemplate2() { doParserTest(); }
  public void testTextBlockTemplate3() { doParserTest(); }
  public void testTextBlockTemplate4() { doParserTest(); }
  public void testTextBlockTemplate5() { doParserTest(); }
  public void testTextBlockTemplate6() { doParserTest(); }
  public void testTextBlockTemplate7() { doParserTest(); }
  public void testTextBlockTemplate8() { doParserTest(); }

  protected abstract void doParserTest(String text);

  protected abstract void doParserTest();
}