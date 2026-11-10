/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.java.psi;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiBinaryExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiLiteralExpression;
import com.intellij.psi.PsiPrefixExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ConstantValuesTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  private PsiClass myClass;

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/psi/constantValues");
    PsiJavaFile file = (PsiJavaFile)myFixture.configureByFile("ClassWithConstants.java");
    myClass = ReadAction.computeBlocking(() -> file.getClasses()[0]);
  }

  @AfterEach
  void tearDown() {
    myClass = null;
  }

  @Test
  public void testInt1() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("INT_CONST1", false);
      assertNotNull(field);
      PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.intType(), initializer.getType());
      assertEquals(Integer.valueOf(1), initializer.getValue());
      assertEquals("1", initializer.getText());

      assertEquals(Integer.valueOf(1), field.computeConstantValue());
    });
  }

  @Test
  public void testInt2() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("INT_CONST2", false);
      assertNotNull(field);
      PsiPrefixExpression initializer = (PsiPrefixExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.intType(), initializer.getType());
      PsiLiteralExpression operand = (PsiLiteralExpression)initializer.getOperand();
      assertNotNull(operand);
      assertEquals(Integer.valueOf(1), operand.getValue());
      assertEquals("-1", initializer.getText());

      assertEquals(Integer.valueOf(-1), field.computeConstantValue());
    });
  }

  @Test
  public void testInt3() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("INT_CONST3", false);
      assertNotNull(field);
      PsiPrefixExpression initializer = (PsiPrefixExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.intType(), initializer.getType());
      int value = -1 << 31;
      assertEquals(Integer.toString(value), initializer.getText());

      assertEquals(Integer.valueOf(value), field.computeConstantValue());
    });
  }

  @Test
  public void testLong1() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("LONG_CONST1", false);
      assertNotNull(field);
      PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals("2", initializer.getText());
      assertEquals(PsiTypes.intType(), initializer.getType());
      assertEquals(Integer.valueOf(2), initializer.getValue());

      assertEquals(Long.valueOf(2), field.computeConstantValue());
    });
  }

  @Test
  public void testLong2() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("LONG_CONST2", false);
      assertNotNull(field);
      PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.longType(), initializer.getType());
      assertEquals(Long.valueOf(1000000000000L), initializer.getValue());
      assertEquals("1000000000000L", initializer.getText());

      assertEquals(Long.valueOf(1000000000000L), field.computeConstantValue());
    });
  }

  @Test
  public void testLong3() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("LONG_CONST3", false);
      assertNotNull(field);
      PsiPrefixExpression initializer = (PsiPrefixExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.longType(), initializer.getType());
      long value = -1L << 63;
      assertEquals(value + "L", initializer.getText());

      assertEquals(Long.valueOf(value), field.computeConstantValue());
    });
  }

  @Test
  public void testShort() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("SHORT_CONST", false);
      assertNotNull(field);
      PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.intType(), initializer.getType());
      assertEquals(Integer.valueOf(3), initializer.getValue());
      assertEquals("3", initializer.getText());

      assertEquals(Short.valueOf((short)3), field.computeConstantValue());
    });
  }

  @Test
  public void testByte() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("BYTE_CONST", false);
      assertNotNull(field);
      PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.intType(), initializer.getType());
      assertEquals(Integer.valueOf(4), initializer.getValue());
      assertEquals("4", initializer.getText());

      assertEquals(Byte.valueOf((byte)4), field.computeConstantValue());
    });
  }

  @Test
  public void testChar() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("CHAR_CONST", false);
      assertNotNull(field);
      PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.charType(), initializer.getType());
      assertEquals(new Character('5'), initializer.getValue());
      assertEquals("'5'", initializer.getText());

      assertEquals(new Character('5'), field.computeConstantValue());
    });
  }

  @Test
  public void testBoolean() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("BOOL_CONST", false);
      assertNotNull(field);
      PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.booleanType(), initializer.getType());
      assertEquals(Boolean.TRUE, initializer.getValue());
      assertEquals("true", initializer.getText());

      assertEquals(Boolean.TRUE, field.computeConstantValue());
    });
  }

  @Test
  public void testFloat() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("FLOAT_CONST", false);
      assertNotNull(field);
      PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.floatType(), initializer.getType());
      assertEquals(new Float(1.234f), initializer.getValue());
      assertEquals("1.234f", initializer.getText());

      assertEquals(new Float(1.234f), field.computeConstantValue());
    });
  }

  @Test
  public void testDouble() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("DOUBLE_CONST", false);
      assertNotNull(field);
      PsiLiteralExpression initializer = (PsiLiteralExpression)field.getInitializer();
      assertNotNull(initializer);
      assertEquals(PsiTypes.doubleType(), initializer.getType());
      assertEquals(new Double(3.456), initializer.getValue());
      assertEquals("3.456", initializer.getText());

      assertEquals(new Double(3.456), field.computeConstantValue());
    });
  }

  @Test
  public void testString() {
    ReadAction.runBlocking(() -> {
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
    });
  }

  @Test
  public void testInfinity() {
    ReadAction.runBlocking(() -> {
      PsiField field1 = myClass.findFieldByName("d1", false);
      assertNotNull(field1);
      PsiReferenceExpression initializer1 = (PsiReferenceExpression)field1.getInitializer();
      assertNotNull(initializer1);
      assertEquals(PsiTypes.doubleType(), initializer1.getType());
      assertEquals("Double.POSITIVE_INFINITY", initializer1.getText());
      assertEquals(new Double(Double.POSITIVE_INFINITY), field1.computeConstantValue());

      PsiField field2 = myClass.findFieldByName("d2", false);
      assertNotNull(field2);
      PsiReferenceExpression initializer2 = (PsiReferenceExpression)field2.getInitializer();
      assertNotNull(initializer2);
      assertEquals(PsiTypes.doubleType(), initializer2.getType());
      assertEquals("Double.NEGATIVE_INFINITY", initializer2.getText());
      assertEquals(new Double(Double.NEGATIVE_INFINITY), field2.computeConstantValue());

      PsiField field3 = myClass.findFieldByName("d3", false);
      assertNotNull(field3);
      PsiReferenceExpression initializer3 = (PsiReferenceExpression)field3.getInitializer();
      assertNotNull(initializer3);
      assertEquals(PsiTypes.doubleType(), initializer3.getType());
      assertEquals("Double.NaN", initializer3.getText());
      assertEquals(new Double(Double.NaN), field3.computeConstantValue());
    });
  }

  @Test
  public void testConstantEvaluatorStackOverflowResistance() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    StringBuilder text = new StringBuilder(65536).append("class X { String s = \"\"");
    for (int i = 0; i < 10000; i++) text.append(" + \"\"");
    text.append("; }");

    PsiJavaFile file = (PsiJavaFile)myFixture.configureByText("a.java", text.toString());
    ReadAction.runBlocking(() -> {
      PsiField field = file.getClasses()[0].findFieldByName("s", false);
      assertNotNull(field);
      assertEquals("", JavaConstantExpressionEvaluator.computeConstantExpression(field.getInitializer(), false));
    });
  }

  @Test
  public void testStringConstExpression1() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("STRING_EXPRESSION_CONST1", false);
      assertNotNull(field);
      PsiBinaryExpression initializer = (PsiBinaryExpression)field.getInitializer();
      assertNotNull(initializer);
      assertTrue(initializer.getType().equalsToText("java.lang.String"));
      assertEquals("\"a\" + \"b\"", initializer.getText());

      assertEquals("ab", field.computeConstantValue());
    });
  }

  @Test
  public void testStringConstExpression2() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("STRING_EXPRESSION_CONST2", false);
      assertNotNull(field);
      PsiBinaryExpression initializer = (PsiBinaryExpression)field.getInitializer();
      assertNotNull(initializer);
      assertTrue(initializer.getType().equalsToText("java.lang.String"));
      assertEquals("\"a\" + 123", initializer.getText());

      assertEquals("a123", field.computeConstantValue());
    });
  }

  @Test
  public void testStringConstExpression3() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("STRING_EXPRESSION_CONST3", false);
      assertNotNull(field);
      PsiBinaryExpression initializer = (PsiBinaryExpression)field.getInitializer();
      assertNotNull(initializer);
      assertTrue(initializer.getType().equalsToText("java.lang.String"));
      assertEquals("123 + \"b\"", initializer.getText());

      assertEquals("123b", field.computeConstantValue());
    });
  }

  @Test
  public void testStringConstExpression4() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("STRING_EXPRESSION_CONST4", false);
      assertNotNull(field);
      PsiBinaryExpression initializer = (PsiBinaryExpression)field.getInitializer();
      assertNotNull(initializer);
      assertTrue(initializer.getType().equalsToText("java.lang.String"));
      assertEquals("INT_CONST1 + \"aaa\"", initializer.getText());

      assertEquals("1aaa", field.computeConstantValue());
    });
  }

  @Test
  public void testStringExpressionClass() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("STRING_EXPRESSION_CLASS", false);
      assertNotNull(field);
      PsiBinaryExpression initializer = (PsiBinaryExpression)field.getInitializer();
      assertNotNull(initializer);
      assertTrue(initializer.getType().equalsToText("java.lang.String"));
      assertEquals("Integer.class + \"xxx\"", initializer.getText());

      assertEquals("class java.lang.Integerxxx", field.computeConstantValue());
    });
  }

  @Test
  public void testStringExpressionClassArray() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("STRING_EXPRESSION_CLASS_ARRAY", false);
      assertNotNull(field);
      PsiBinaryExpression initializer = (PsiBinaryExpression)field.getInitializer();
      assertNotNull(initializer);
      assertTrue(initializer.getType().equalsToText("java.lang.String"));
      assertEquals("Integer[].class + \"xxx\"", initializer.getText());

      assertEquals("class [Ljava.lang.Integer;xxx", field.computeConstantValue());
    });
  }

  @Test
  public void testStringExpressionInterface() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("STRING_EXPRESSION_INTERFACE", false);
      assertNotNull(field);
      PsiBinaryExpression initializer = (PsiBinaryExpression)field.getInitializer();
      assertNotNull(initializer);
      assertTrue(initializer.getType().equalsToText("java.lang.String"));
      assertEquals("Runnable.class + \"xxx\"", initializer.getText());

      assertEquals("interface java.lang.Runnablexxx", field.computeConstantValue());
    });
  }

  @Test
  public void testStringExpressionPrimitive() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("STRING_EXPRESSION_PRIMITIVE", false);
      assertNotNull(field);
      PsiBinaryExpression initializer = (PsiBinaryExpression)field.getInitializer();
      assertNotNull(initializer);
      assertTrue(initializer.getType().equalsToText("java.lang.String"));
      assertEquals("int.class + \"xxx\"", initializer.getText());

      assertEquals("intxxx", field.computeConstantValue());
    });
  }

  @Test
  public void testStringExpressionPrimitiveArray() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("STRING_EXPRESSION_PRIMITIVE_ARRAY", false);
      assertNotNull(field);
      PsiBinaryExpression initializer = (PsiBinaryExpression)field.getInitializer();
      assertNotNull(initializer);
      assertTrue(initializer.getType().equalsToText("java.lang.String"));
      assertEquals("int[].class + \"xxx\"", initializer.getText());

      assertEquals("class [Ixxx", field.computeConstantValue());
    });
  }

  @Test
  public void testStringExpressionMethod() {
    ReadAction.runBlocking(() -> {
      PsiField field = myClass.findFieldByName("STRING_EXPRESSION_METHOD", false);
      assertNotNull(field);
      PsiBinaryExpression initializer = (PsiBinaryExpression)field.getInitializer();
      assertNotNull(initializer);
      assertTrue(initializer.getType().equalsToText("java.lang.String"));
      assertEquals("val() + \"xxx\"", initializer.getText());

      assertNull(field.computeConstantValue());
    });
  }
}
