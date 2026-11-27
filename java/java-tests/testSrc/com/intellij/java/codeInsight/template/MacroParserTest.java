// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.template;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
import com.intellij.codeInsight.template.impl.ConstantNode;
import com.intellij.codeInsight.template.impl.EmptyNode;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.MacroParser;
import com.intellij.codeInsight.template.impl.VariableNode;
import com.intellij.codeInsight.template.macro.VariableOfTypeMacro;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;


@TestApplication
public class MacroParserTest {

  @Test
  public void testEmpty() {
    Expression e = MacroParser.parse("");
    assertTrue(e instanceof ConstantNode);
    assertEquals("", e.calculateResult(null).toString());
  }

  @Test
  public void testFunction() {
    Expression e = MacroParser.parse("  variableOfType(  \"java.util.Collection\"  )   ");
    assertTrue(e instanceof MacroCallNode);
    MacroCallNode n = (MacroCallNode) e;
    assertTrue(n.getMacro() instanceof VariableOfTypeMacro);
    Expression[] parameters = n.getParameters();
    assertEquals(1, parameters.length);
    assertTrue(parameters [0] instanceof ConstantNode);
    ConstantNode cn = (ConstantNode) parameters [0];
    assertEquals("java.util.Collection", cn.calculateResult(null).toString());
  }

  @Test
  public void testVariable() {
    Expression e = MacroParser.parse("variableOfType(E=\"t\")");
    Expression[] parameters = ((MacroCallNode) e).getParameters();
    assertEquals(1, parameters.length);
    assertTrue(parameters [0] instanceof VariableNode);
    VariableNode vn = (VariableNode) parameters [0];
    assertEquals("E", vn.getName());
    assertTrue(vn.getInitialValue() instanceof ConstantNode);
  }

  @Test
  public void testEnd() {
    Expression e = MacroParser.parse("END");
    assertTrue(e instanceof EmptyNode);
  }

  @Test
  public void testMultipleParams() {
    Expression e = MacroParser.parse("variableOfType(\"A\", \"B\")");
    assertTrue(e instanceof MacroCallNode);
    MacroCallNode n = (MacroCallNode) e;
    assertTrue(n.getMacro() instanceof VariableOfTypeMacro);
    Expression[] parameters = n.getParameters();
    assertEquals(2, parameters.length);
    assertTrue(parameters [0] instanceof ConstantNode);
    assertTrue(parameters [1] instanceof ConstantNode);
  }

  @Test
  public void testSlashEscape() {
    Expression e = MacroParser.parse("\"test\\\\test\\n\\t\\f\\x\"");
    Result result = assertInstanceOf(ConstantNode.class, e).calculateResult(null);
    assertEquals("test\\test\n\t\fx", assertInstanceOf(TextResult.class, result).getText());
  }

  @Test
  public void testSingleQuote() {
    Expression e = MacroParser.parse("\"");
    Result result = assertInstanceOf(ConstantNode.class, e).calculateResult(null);
    assertEquals("", assertInstanceOf(TextResult.class, result).getText());
  }
}
