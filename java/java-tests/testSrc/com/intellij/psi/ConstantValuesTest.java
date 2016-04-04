/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.JavaTestUtil;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class ConstantValuesTest extends LightCodeInsightFixtureTestCase {
  private PsiClass myClass;

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/psi/constantValues";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myClass = ((PsiJavaFile)myFixture.configureByFile("ClassWithConstants.java")).getClasses()[0];
  }

  public void testInt1() {
    PsiField field = myClass.findFieldByName("INT_CONST1", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    assertEquals(Integer.valueOf(1), initializer.getValue());
    assertEquals("1", initializer.getText());

    assertEquals(Integer.valueOf(1), field.computeConstantValue());
  }

  public void testInt2() {
    PsiField field = myClass.findFieldByName("INT_CONST2", false);
    assertNotNull(field);
    PsiPrefixExpression initializer = (PsiPrefixExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    PsiLiteralExpression operand = (PsiLiteralExpression)initializer.getOperand();
    assertNotNull(operand);
    assertEquals(Integer.valueOf(1), operand.getValue());
    assertEquals("-1", initializer.getText());

    assertEquals(Integer.valueOf(-1), field.computeConstantValue());
  }

  public void testInt3() {
    PsiField field = myClass.findFieldByName("INT_CONST3", false);
    assertNotNull(field);
    PsiPrefixExpression initializer = (PsiPrefixExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    int value = -1 << 31;
    assertEquals(Integer.toString(value), initializer.getText());

    assertEquals(Integer.valueOf(value), field.computeConstantValue());
  }

  public void testLong1() {
    PsiField field = myClass.findFieldByName("LONG_CONST1", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals("2", initializer.getText());
    assertEquals(PsiType.INT, initializer.getType());
    assertEquals(Integer.valueOf(2), initializer.getValue());

    assertEquals(Long.valueOf(2), field.computeConstantValue());
  }

  public void testLong2() {
    PsiField field = myClass.findFieldByName("LONG_CONST2", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.LONG, initializer.getType());
    assertEquals(Long.valueOf(1000000000000L), initializer.getValue());
    assertEquals("1000000000000L", initializer.getText());

    assertEquals(Long.valueOf(1000000000000L), field.computeConstantValue());
  }

  public void testLong3() {
    PsiField field = myClass.findFieldByName("LONG_CONST3", false);
    assertNotNull(field);
    PsiPrefixExpression initializer = (PsiPrefixExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.LONG, initializer.getType());
    long value = -1L << 63;
    assertEquals(value + "L", initializer.getText());

    assertEquals(Long.valueOf(value), field.computeConstantValue());
  }

  public void testShort() {
    PsiField field = myClass.findFieldByName("SHORT_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    assertEquals(Integer.valueOf(3), initializer.getValue());
    assertEquals("3", initializer.getText());

    assertEquals(Short.valueOf((short)3), field.computeConstantValue());
  }

  public void testByte() {
    PsiField field = myClass.findFieldByName("BYTE_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.INT, initializer.getType());
    assertEquals(Integer.valueOf(4), initializer.getValue());
    assertEquals("4", initializer.getText());

    assertEquals(Byte.valueOf((byte)4), field.computeConstantValue());
  }

  public void testChar() {
    PsiField field = myClass.findFieldByName("CHAR_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.CHAR, initializer.getType());
    assertEquals(new Character('5'), initializer.getValue());
    assertEquals("'5'", initializer.getText());

    assertEquals(new Character('5'), field.computeConstantValue());
  }

  public void testBoolean() {
    PsiField field = myClass.findFieldByName("BOOL_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.BOOLEAN, initializer.getType());
    assertEquals(Boolean.TRUE, initializer.getValue());
    assertEquals("true", initializer.getText());

    assertEquals(Boolean.TRUE, field.computeConstantValue());
  }

  public void testFloat() {
    PsiField field = myClass.findFieldByName("FLOAT_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.FLOAT, initializer.getType());
    assertEquals(new Float(1.234f), initializer.getValue());
    assertEquals("1.234f", initializer.getText());

    assertEquals(new Float(1.234f), field.computeConstantValue());
  }

  public void testDouble() {
    PsiField field = myClass.findFieldByName("DOUBLE_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    assertEquals(PsiType.DOUBLE, initializer.getType());
    assertEquals(new Double(3.456), initializer.getValue());
    assertEquals("3.456", initializer.getText());

    assertEquals(new Double(3.456), field.computeConstantValue());
  }

  public void testString() {
    PsiField field = myClass.findFieldByName("STRING_CONST", false);
    assertNotNull(field);
    PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
    assertNotNull(initializer);
    PsiType type = initializer.getType();
    assertNotNull(type);
    assertTrue(type.equalsToText("java.lang.String"));
    assertEquals("a\r\n\"bcd", initializer.getValue());
    assertEquals("\"a\\r\\n\\\"bcd\"", initializer.getText());

    assertEquals("a\r\n\"bcd", field.computeConstantValue());
  }

  public void testInfinity() {
    PsiField field1 = myClass.findFieldByName("d1", false);
    assertNotNull(field1);
    PsiReferenceExpression initializer1 = (PsiReferenceExpression)field1.getInitializer();
    assertNotNull(initializer1);
    assertEquals(PsiType.DOUBLE, initializer1.getType());
    assertEquals("Double.POSITIVE_INFINITY", initializer1.getText());
    assertEquals(new Double(Double.POSITIVE_INFINITY), field1.computeConstantValue());

    PsiField field2 = myClass.findFieldByName("d2", false);
    assertNotNull(field2);
    PsiReferenceExpression initializer2 = (PsiReferenceExpression)field2.getInitializer();
    assertNotNull(initializer2);
    assertEquals(PsiType.DOUBLE, initializer2.getType());
    assertEquals("Double.NEGATIVE_INFINITY", initializer2.getText());
    assertEquals(new Double(Double.NEGATIVE_INFINITY), field2.computeConstantValue());

    PsiField field3 = myClass.findFieldByName("d3", false);
    assertNotNull(field3);
    PsiReferenceExpression initializer3 = (PsiReferenceExpression)field3.getInitializer();
    assertNotNull(initializer3);
    assertEquals(PsiType.DOUBLE, initializer3.getType());
    assertEquals("Double.NaN", initializer3.getText());
    assertEquals(new Double(Double.NaN), field3.computeConstantValue());
  }

  public void testConstantEvaluatorStackOverflowResistance() {
    StringBuilder text = new StringBuilder(65536).append("class X { String s = \"\"");
    for (int i = 0; i < 10000; i++) text.append(" + \"\"");
    text.append("; }");

    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("a.java", text.toString());
    PsiField field = file.getClasses()[0].findFieldByName("s", false);
    assertNotNull(field);
    assertEquals("", JavaConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), false));
  }
}