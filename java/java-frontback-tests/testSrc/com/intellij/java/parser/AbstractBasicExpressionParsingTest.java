// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.parser;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

public abstract class AbstractBasicExpressionParsingTest extends AbstractBasicJavaParsingTestCase {
  public AbstractBasicExpressionParsingTest(@NotNull AbstractBasicJavaParsingTestConfigurator configurator) {
    super("parser-full/expressionParsing", configurator);
  }

  @Test
  public void testAssignment0() { doTest(true); }
  @Test
  public void testAssignment1() { doTest(true); }

  @Test
  public void testCond0() { doTest(true); }
  @Test
  public void testCond1() { doTest(true); }
  @Test
  public void testCond2() { doTest(true); }
  @Test
  public void testCond3() { doTest(true); }

  @Test
  public void testCondOr0() { doTest(true); }
  @Test
  public void testCondOr1() { doTest(true); }

  @Test
  public void testCondAnd0() { doTest(true); }
  @Test
  public void testCondAnd1() { doTest(true); }

  @Test
  public void testOr0() { doTest(true); }
  @Test
  public void testOr1() { doTest(true); }

  @Test
  public void testXor0() { doTest(true); }
  @Test
  public void testXor1() { doTest(true); }

  @Test
  public void testAnd0() { doTest(true); }
  @Test
  public void testAnd1() { doTest(true); }

  @Test
  public void testInstanceOf0() { doTest(true); }
  @Test
  public void testInstanceOf1() { doTest(true); }

  @Test
  public void testNot0() { doTest(true); }
  @Test
  public void testNot1() { doTest(true); }

  @Test
  public void testCast0() { doTest(true); }

  @Test
  public void testParenth() { doTest(true); }
  @Test
  public void testParenth1() { doTest(true); }
  public void _testParenth2() { doTest(true); }

  @Test
  public void testNewInExprList() { doTest(true); }

  @Test
  public void testNew0() { doTest(true); }
  @Test
  public void testNew1() { doTest(true); }
  @Test
  public void testNew2() { doTest(true); }
  @Test
  public void testNew3() { doTest(true); }
  @Test
  public void testNew4() { doTest(true); }
  @Test
  public void testNew5() { doTest(true); }
  @Test
  public void testNew6() { doTest(true); }
  @Test
  public void testNew7() { doTest(true); }
  @Test
  public void testNew8() { doTest(true); }
  @Test
  public void testNew9() { doTest(true); }
  @Test
  public void testNew10() { doTest(true); }
  @Test
  public void testNew11() { doTest(true); }
  @Test
  public void testNew12() { doTest(true); }
  @Test
  public void testNew13() { doTest(true); }
  @Test
  public void testNew14() { doTest(true); }
  @Test
  public void testNew15() { doTest(true); }

  @Test
  public void testExprList0() { doTest(true); }
  @Test
  public void testExprList1() { doTest(true); }
  @Test
  public void testExprList2() { doTest(true); }
  @Test
  public void testExprList3() { doTest(true); }
  @Test
  public void testExprList4() { doTest(true); }
  @Test
  public void testExprList5() { doTest(true); }
  @Test
  public void testExprList6() { doTest(true); }

  @Test
  public void testArrayInitializer0() { doTest(true); }
  @Test
  public void testArrayInitializer1() { doTest(true); }
  @Test
  public void testArrayInitializer2() { doTest(true); }
  @Test
  public void testArrayInitializer3() { doTest(true); }
  @Test
  public void testArrayInitializer4() { doTest(true); }
  @Test
  public void testArrayInitializer5() { doTest(true); }
  @Test
  public void testArrayInitializer6() { doTest(true); }
  @Test
  public void testArrayInitializer7() { doTest(true); }

  @Test
  public void testPinesInReferenceExpression0() { doTest(true); }
  @Test
  public void testPinesInReferenceExpression1() { doTest(true); }

  @Test
  public void testGE() { doTest(true); }
  @Test
  public void testIncompleteCast() { doTest(true); }
  @Test
  public void testShiftRight() { doTest(true); }
  @Test
  public void testAnonymousErrors() { doTest(true); }
  @Test
  public void testAnonymousErrors1() { doTest(true); }
  @Test
  public void testAnonymousWithTypeParams() { doTest(true); }
  @Test
  public void testAnonymous2() { doTest(true); }
  @Test
  public void testIncompleteDecl() { doTest(true); }
  @Test
  public void testLambdaConfusion() { doTest(true); }
  @Test
  public void testNestedLambda() { doTest(true); }

  @Test
  public void testIllegalWildcard() { doTest(true); }

  @Test
  public void testQualifiedSuperMethodCall() { doTest(true); }
  @Test
  public void testQualifiedSuperMethodCall1() { doTest(true); }
  @Test
  public void testSuperMethodCallTypeParameterList() { doTest(true); }

  @Test
  public void testPrimitiveClassObjectAccess() { doTest(true); }
  @Test
  public void testPrimitiveClassObjectAccessError() { doTest(true); }
  @Test
  public void testPrimitiveArrayClassObjectAccessError() { doTest(true); }
}