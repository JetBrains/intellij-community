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
import com.intellij.lang.java.parser.ExpressionParser;
import com.intellij.lang.java.parser.JavaParsingTestCase;


public class ExpressionParserTest extends JavaParsingTestCase {
  public ExpressionParserTest() {
    super("parser-partial/expressions");
  }

  public void testAssignment0() { doParserTest("a = 0"); }
  public void testAssignment1() { doParserTest("a ="); }

  public void testCond0() { doParserTest("cond ? true : false"); }
  public void testCond1() { doParserTest("cond ?"); }
  public void testCond2() { doParserTest("cond ? true"); }
  public void testCond3() { doParserTest("cond ? true :"); }

  public void testCondOr0() { doParserTest("a || b || c"); }
  public void testCondOr1() { doParserTest("a ||"); }

  public void testOr0() { doParserTest("a | b | c"); }
  public void testOr1() { doParserTest("a |"); }

  public void testInstanceOf0() { doParserTest("a instanceof String"); }
  public void testInstanceOf1() { doParserTest("a instanceof"); }

  public void testNot0() { doParserTest("!!a"); }
  public void testNot1() { doParserTest("!"); }

  public void testCast() { doParserTest("(Type)var"); }

  public void testParenth0() { doParserTest("(c)"); }
  public void testParenth1() { doParserTest("(this).f--"); }

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

  public void testPinesInReferenceExpression0() { doParserTest("Collections.<String>sort(null)"); }
  public void testPinesInReferenceExpression1() { doParserTest("this.<String>sort(null)"); }

  public void testGE() { doParserTest("x >>>= 8 >> 2"); }

  public void testIncompleteCast() { doParserTest("f((ArrayList<String>) )"); }
  public void testShiftRight() { doParserTest("x >>= 2"); }

  public void testIllegalWildcard() { doParserTest("this.<?>foo()"); }

  public void testQualifiedSuperMethodCall0() { doParserTest("new D().super(0)"); }
  public void testQualifiedSuperMethodCall1() { doParserTest("d.super(0)"); }
  public void testSuperMethodCallTypeParameterList() { doParserTest("super()"); }
  public void testPrimitiveClassObjectAccess() { doParserTest("int.class"); }

  private void doParserTest(final String text) {
    doParserTest(text, new Parser() {
      public void parse(final PsiBuilder builder) {
        ExpressionParser.parse(builder);
      }
    });
  }
}
