// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser.partial;

import com.intellij.java.parser.AbstractBasicJavaParsingTestCase;
import com.intellij.java.parser.AbstractBasicJavaParsingTestConfigurator;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicExpressionParserTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicExpressionParserTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-partial/expressions", configurator);
  }

  @Test
  public void testAssignment0() { doParserTest("a = 0"); }
  @Test
  public void testAssignment1() { doParserTest("a ="); }

  @Test
  public void testBinary0() { doParserTest("a + b"); }
  @Test
  public void testBinary1() { doParserTest("a < b"); }
  @Test
  public void testBinary2() { doParserTest("a > = b"); }
  @Test
  public void testBinary3() { doParserTest("a >/**/= b"); }
  @Test
  public void testBinary4() { doParserTest("a**"); }

  @Test
  public void testCond0() { doParserTest("cond ? true : false"); }
  @Test
  public void testCond1() { doParserTest("cond ?"); }
  @Test
  public void testCond2() { doParserTest("cond ? true"); }
  @Test
  public void testCond3() { doParserTest("cond ? true :"); }
  @Test
  public void testCond4() { doParserTest("cond ? true : false ? true : false"); }

  @Test
  public void testCondOr0() { doParserTest("a || b || c"); }
  @Test
  public void testCondOr1() { doParserTest("a ||"); }

  @Test
  public void testOr0() { doParserTest("a | b | c"); }
  @Test
  public void testOr1() { doParserTest("a |"); }

  @Test
  public void testInstanceOf0() { doParserTest("a instanceof String"); }
  @Test
  public void testInstanceOf1() { doParserTest("a instanceof"); }

  @Test
  public void testInstanceOfPattern0() { doParserTest("x instanceof Foo v"); }
  @Test
  public void testInstanceOfPattern1() { doParserTest("x instanceof final Foo v"); }
  @Test
  public void testInstanceOfPattern2() { doParserTest("x instanceof @Ann() final Foo v"); }
  @Test
  public void testInstanceOfPattern3() { doParserTest("x instanceof (Foo v)"); }
  @Test
  public void testInstanceOfPattern4() { doParserTest("x instanceof Foo v && v > 10"); }

  @Test
  public void testNot0() { doParserTest("!!a"); }
  @Test
  public void testNot1() { doParserTest("!"); }

  @Test
  public void testCast0() { doParserTest("(Type)var"); }
  @Test
  public void testCast1() { doParserTest("(double)1 / 5"); }

  @Test
  public void testParenth0() { doParserTest("(c)"); }
  @Test
  public void testParenth1() { doParserTest("(this).f--"); }
  @Test
  public void testParenth2() { doParserTest("("); }
  @Test
  public void testParenth3() { doParserTest("(a < b)"); }

  @Test
  public void testNewInExprList() { doParserTest("call(new)"); }

  @Test
  public void testNew0() { doParserTest("new A()"); }
  @Test
  public void testNew1() { doParserTest("new A[1][]"); }
  @Test
  public void testNew2() { doParserTest("new A[]"); }
  @Test
  public void testNew3() { doParserTest("new A[][1]"); }
  @Test
  public void testNew4() { doParserTest("new A[][]{null}"); }
  @Test
  public void testNew5() { doParserTest("new A[1]{null}"); }
  @Test
  public void testNew6() { doParserTest("new"); }
  @Test
  public void testNew7() { doParserTest("new A"); }
  @Test
  public void testNew8() { doParserTest("new A["); }
  @Test
  public void testNew9() { doParserTest("new int"); }
  @Test
  public void testNew10() { doParserTest("new int()"); }
  @Test
  public void testNew11() { doParserTest("new String[0"); }
  @Test
  public void testNew12() { doParserTest("new int[1][2]"); }
  @Test
  public void testNew13() { doParserTest("new int[1][][2]"); }
  @Test
  public void testNew14() { doParserTest("Q.new A()"); }
  @Test
  public void testNew15() { doParserTest("new C<?>.B()"); }
  @Test
  public void testNew16() { doParserTest("new C<>()"); }
  @Test
  public void testNew17() { doParserTest("new Map<String, >()"); }
  @Test
  public void testNew18() { doParserTest("new int @A [2] @B"); }
  @Test
  public void testNew19() { doParserTest("new A(f; o)"); }

  @Test
  public void testExprList0() { doParserTest("f(1,2)"); }
  @Test
  public void testExprList1() { doParserTest("f("); }
  @Test
  public void testExprList2() { doParserTest("f(1"); }
  @Test
  public void testExprList3() { doParserTest("f(1,"); }
  @Test
  public void testExprList4() { doParserTest("f(1,)"); }
  @Test
  public void testExprList5() { doParserTest("f(1,,2)"); }
  @Test
  public void testExprList6() { doParserTest("f(,2)"); }

  @Test
  public void testArrayInitializer0() { doParserTest("{ }"); }
  @Test
  public void testArrayInitializer1() { doParserTest("{ 1 }"); }
  @Test
  public void testArrayInitializer2() { doParserTest("{ 1, }"); }
  @Test
  public void testArrayInitializer3() { doParserTest("{ 1,2 }"); }
  @Test
  public void testArrayInitializer4() { doParserTest("{ 1 2 }"); }
  @Test
  public void testArrayInitializer5() { doParserTest("{ { }"); }
  @Test
  public void testArrayInitializer6() { doParserTest("{  ,  }"); }
  @Test
  public void testArrayInitializer7() { doParserTest("{  ,  , 7 }"); }
  @Test
  public void testArrayInitializer8() { doParserTest("{ 8,  ,  , }"); }
  @Test
  public void testArrayInitializer9() { doParserTest("{  , 9 }"); }

  @Test
  public void testPinesInReferenceExpression0() { doParserTest("Collections.<String>sort(null)"); }
  @Test
  public void testPinesInReferenceExpression1() { doParserTest("this.<String>sort(null)"); }
  @Test
  public void testPinesInReferenceExpression2() { doParserTest("<String>super(null)"); }

  @Test
  public void testGE0() { doParserTest("x >>>= 8 >> 2"); }
  @Test
  public void testGE1() { doParserTest("x >= 2"); }

  @Test
  public void testIncompleteCast() { doParserTest("f((ArrayList<String>) )"); }

  @Test
  public void testShiftRight() { doParserTest("x >>= 2"); }

  @Test
  public void testCorruptAnnoInCast() { doParserTest("(@&"); }

  @Test
  public void testIllegalWildcard() { doParserTest("this.<?>foo()"); }

  @Test
  public void testIllegalBound() { doParserTest("C.<T extends S>foo()"); }

  @Test
  public void testQualifiedSuperMethodCall0() { doParserTest("new D().super(0)"); }
  @Test
  public void testQualifiedSuperMethodCall1() { doParserTest("d.super(0)"); }
  @Test
  public void testQualifiedSuperMethodCall2() { doParserTest("(new O()).<T>super()"); }
  @Test
  public void testQualifiedSuperMethodCall3() { doParserTest("C.A.super()"); }

  @Test
  public void testSuperMethodCallTypeParameterList() { doParserTest("super()"); }

  @Test
  public void testPrimitiveClassObjectAccess() { doParserTest("int.class"); }

  @Test
  public void testPrimitiveFieldAccess() { doParserTest("int.x"); }

  @Test
  public void testChainedClassObjectAccess() { doParserTest("A.class.B.class"); }
  @Test
  public void testChainedThisObjectAccess() { doParserTest("A.this.B.this"); }

  @Test
  public void testAnnotatedRefExpr0() { doParserTest("@A C1.@B() C2"); }
  @Test
  public void testAnnotatedRefExpr1() { doParserTest("@A C1.@B() ()"); }

  @Test
  public void testMethodRef0() { doParserTest("a.b.C::m"); }
  @Test
  public void testMethodRef1() { doParserTest("a.b.C<T>::new"); }
  @Test
  public void testMethodRef2() { doParserTest("C::<T>m"); }
  @Test
  public void testMethodRef3() { doParserTest("a[i]::m"); }
  @Test
  public void testMethodRef4() { doParserTest("int[]::clone"); }
  @Test
  public void testMethodRef5() { doParserTest("(f ? list.map(String::length) : Collections.emptyList())::iterator"); }

  @Test
  public void testLambdaExpression0() { doParserTest("p -> 42"); }
  @Test
  public void testLambdaExpression1() { doParserTest("p -> "); }
  @Test
  public void testLambdaExpression2() { doParserTest("p -> {"); }
  @Test
  public void testLambdaExpression3() { doParserTest("(p) -> { }"); }
  @Test
  public void testLambdaExpression4() { doParserTest("(p, v) -> null"); }
  @Test
  public void testLambdaExpression5() { doParserTest("(p, v -> null"); }
  @Test
  public void testLambdaExpression6() { doParserTest("(p, v, -> null"); }
  @Test
  public void testLambdaExpression7() { doParserTest("(p -> null)"); }
  @Test
  public void testLambdaExpression8() { doParserTest("(I)(p) -> null"); }
  @Test
  public void testLambdaExpression9() { doParserTest("(I)p -> null"); }
  @Test
  public void testLambdaExpression10() { doParserTest("(I)(p -> null)"); }
  @Test
  public void testLambdaExpression11() { doParserTest("() -> { }"); }
  @Test
  public void testLambdaExpression12() { doParserTest("(I1 & I2) () -> null"); }
  @Test
  public void testLambdaExpression13() { doParserTest("(I1 & I2) () -> {}"); }
  @Test
  public void testLambdaExpression14() { doParserTest("(String t) -> t"); }
  @Test
  public void testLambdaExpression15() { doParserTest("(int a, int b) -> a + b"); }
  @Test
  public void testLambdaExpression16() { doParserTest("(final int x) -> x"); }
  @Test
  public void testLambdaExpression17() { doParserTest("(String s -> s"); }
  @Test
  public void testLambdaExpression18() { doParserTest("(java.lang.String s, -> s"); }
  @Test
  public void testLambdaExpression19() { doParserTest("(@A T t) -> (null)"); }
  @Test
  public void testLambdaExpression20() { doParserTest("(@A T) -> (null)"); }
  @Test
  public void testLambdaExpression21() { doParserTest("(T @A() [] x) -> { }"); }
  @Test
  public void testLambdaExpression22() { doParserTest("(T x @A() []) -> { }"); }
  @Test
  public void testLambdaExpression23() { doParserTest("(T... t) -> t.length"); }
  @Test
  public void testLambdaExpression24() { doParserTest("var -> var"); }
  @Test
  public void testLambdaExpression25() { doParserTest("(var) -> var"); }
  @Test
  public void testLambdaExpression26() { doParserTest("(var var) -> var"); }
  @Test
  public void testLambdaExpression27() { doParserTest("z > (w) -> v"); }

  @Test
  public void testTextBlockLiteral0() { doParserTest("\"\"\".\"\"\""); }

  @Test
  public void testSwitch0() { doParserTest("switch (i) { case 1 -> 1; default -> 2; }"); }

  @Test
  public void testYieldAsExpr0() { doParserTest("yield.run()"); }
  @Test
  public void testYieldAsExpr1() { doParserTest("yield++"); }
  @Test
  public void testYieldAsExpr2() { doParserTest("yield += 2"); }
  @Test
  public void testYieldAsExpr3() { doParserTest("yield ? 10 : 20"); }

  @Test
  public void testStringTemplate1() { doParserTest(); }
  @Test
  public void testStringTemplate2() { doParserTest(); }
  @Test
  public void testStringTemplate3() { doParserTest(); }
  @Test
  public void testStringTemplate4() { doParserTest(); }
  @Test
  public void testStringTemplate5() { doParserTest(); }
  @Test
  public void testStringTemplate6() { doParserTest(); }
  @Test
  public void testStringTemplate7() { doParserTest(); }
  @Test
  public void testStringTemplate8() { doParserTest(); }

  @Test
  public void testTextBlockTemplate1() { doParserTest(); }
  @Test
  public void testTextBlockTemplate2() { doParserTest(); }
  @Test
  public void testTextBlockTemplate3() { doParserTest(); }
  @Test
  public void testTextBlockTemplate4() { doParserTest(); }
  @Test
  public void testTextBlockTemplate5() { doParserTest(); }
  @Test
  public void testTextBlockTemplate6() { doParserTest(); }
  @Test
  public void testTextBlockTemplate7() { doParserTest(); }
  @Test
  public void testTextBlockTemplate8() { doParserTest(); }

  protected abstract void doParserTest(String text);

  protected abstract void doParserTest();
}