package com.intellij.codeInsight.template;

import com.intellij.codeInsight.template.impl.*;
import com.intellij.codeInsight.template.macro.VariableOfTypeMacro;
import com.intellij.testFramework.LightIdeaTestCase;

/**
 * @author yole
 */
public class MacroParserTest extends LightIdeaTestCase {

  public void testEmpty() {
    Expression e = MacroParser.parse("");
    assertTrue(e instanceof ConstantNode);
    assertEquals("", e.calculateResult(null).toString());
  }

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

  public void testVariable() {
    Expression e = MacroParser.parse("variableOfType(E=\"t\")");
    Expression[] parameters = ((MacroCallNode) e).getParameters();
    assertEquals(1, parameters.length);
    assertTrue(parameters [0] instanceof VariableNode);
    VariableNode vn = (VariableNode) parameters [0];
    assertEquals("E", vn.getName());
    assertTrue(vn.getInitialValue() instanceof ConstantNode);
  }

  public void testEnd() {
    Expression e = MacroParser.parse("END");
    assertTrue(e instanceof EmptyNode);
  }

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

  public void testSlashEscape() {
    Expression e = MacroParser.parse("\"test\\\\test\\n\\t\\f\\x\"");
    Result result = assertInstanceOf(e, ConstantNode.class).calculateResult(null);
    assertEquals("test\\test\n\t\fx", assertInstanceOf(result, TextResult.class).getText());
  }
}
