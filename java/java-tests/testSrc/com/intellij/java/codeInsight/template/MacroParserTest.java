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
package com.intellij.java.codeInsight.template;

import com.intellij.codeInsight.template.Expression;
import com.intellij.codeInsight.template.Result;
import com.intellij.codeInsight.template.TextResult;
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
