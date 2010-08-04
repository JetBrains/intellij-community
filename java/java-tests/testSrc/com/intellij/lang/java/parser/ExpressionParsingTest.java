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
package com.intellij.lang.java.parser;


public class ExpressionParsingTest extends JavaParsingTestCase {

  public ExpressionParsingTest() {
    super("parser-full/expressionParsing");
  }

  public void testAssignment0() { doTest(true); }
  public void testAssignment1() { doTest(true); }

  public void testCond0() { doTest(true); }
  public void testCond1() { doTest(true); }
  public void testCond2() { doTest(true); }
  public void testCond3() { doTest(true); }

  public void testCondOr0() { doTest(true); }
  public void testCondOr1() { doTest(true); }

  public void testCondAnd0() { doTest(true); }
  public void testCondAnd1() { doTest(true); }

  public void testOr0() { doTest(true); }
  public void testOr1() { doTest(true); }

  public void testXor0() { doTest(true); }
  public void testXor1() { doTest(true); }

  public void testAnd0() { doTest(true); }
  public void testAnd1() { doTest(true); }

  public void testInstanceOf0() { doTest(true); }
  public void testInstanceOf1() { doTest(true); }

  public void testNot0() { doTest(true); }
  public void testNot1() { doTest(true); }

  public void testCast0() { doTest(true); }

  public void testParenth() { doTest(true); }
  public void testParenth1() { doTest(true); }

  public void testNewInExprList() { doTest(true); }

  public void testNew0() { doTest(true); }
  public void testNew1() { doTest(true); }
  public void testNew2() { doTest(true); }
  public void testNew3() { doTest(true); }
  public void testNew4() { doTest(true); }
  public void testNew5() { doTest(true); }
  public void testNew6() { doTest(true); }
  public void testNew7() { doTest(true); }
  public void testNew8() { doTest(true); }
  public void testNew9() { doTest(true); }
  public void testNew10() { doTest(true); }
  public void testNew11() { doTest(true); }
  public void testNew12() { doTest(true); }
  public void testNew13() { doTest(true); }
  public void testNew14() { doTest(true); }

  public void testExprList0() { doTest(true); }
  public void testExprList1() { doTest(true); }
  public void testExprList2() { doTest(true); }
  public void testExprList3() { doTest(true); }
  public void testExprList4() { doTest(true); }
  public void testExprList5() { doTest(true); }
  public void testExprList6() { doTest(true); }

  public void testArrayInitializer0() { doTest(true); }
  public void testArrayInitializer1() { doTest(true); }
  public void testArrayInitializer2() { doTest(true); }
  public void testArrayInitializer3() { doTest(true); }
  public void testArrayInitializer4() { doTest(true); }
  public void testArrayInitializer5() { doTest(true); }
  public void testArrayInitializer6() { doTest(true); }
  public void testArrayInitializer7() { doTest(true); }

  public void testPinesInReferenceExpression0() { doTest(true); }
  public void testPinesInReferenceExpression1() { doTest(true); }

  public void testGE() { doTest(true); }
  public void testIncompleteCast() { doTest(true); }
  public void testShiftRight() { doTest(true); }
  public void testAnonymousErrors() { doTest(true); }
  public void testAnonymousErrors1() { doTest(true); }
  public void testAnonymousWithTypeParams() { doTest(true); }
  public void testAnonymous2() { doTest(true); }
  public void testIncompleteDecl() { doTest(true); }

  public void testIllegalWildcard() { doTest(true); }

  public void testQualifiedSuperMethodCall() { doTest(true); }
  public void testQualifiedSuperMethodCall1() { doTest(true); }
  public void testSuperMethodCallTypeParameterList() { doTest(true); }

  public void testPrimitiveClassObjectAccess() { doTest(true); }
}