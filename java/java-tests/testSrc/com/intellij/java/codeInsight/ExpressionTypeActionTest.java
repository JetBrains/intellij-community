// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.hint.ShowExpressionTypeHandler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * @author gregsh
 */
public class ExpressionTypeActionTest extends JavaCodeInsightFixtureTestCase {
  public void testSimpleStr1() { doSimpleTest("\"\"<caret>", "String, int"); }
  public void testSimpleStr2() { doSimpleTest("<selection>\"\"</selection><caret>", "String"); }
  public void testSimpleStr3() { doSimpleTest("\"\"<caret>,", "String, int"); }
  public void testSimpleStr4() { doSimpleTest("\"\"<caret>\n", "String, int"); }
  public void testSimpleChr1() { doSimpleTest("\"\" + 'x'<caret>", "char, String, int"); }
  public void testSimpleChr2() { doSimpleTest("\"\" + <selection>'x'</selection><caret>", "char"); }
  public void testSimpleInt1() { doSimpleTest("111<caret>", "int, int"); }
  public void testSimpleInt2() { doSimpleTest("<selection>111</selection><caret>", "int"); }
  public void testSimpleArg1() { doSimpleTest("firstArg<caret>", "long[], int"); }
  public void testSimpleArg2() { doSimpleTest("<selection>firstArg</selection><caret>", "long[]"); }
  public void testSimpleArr1() { doSimpleTest("firstArg[0]<caret>", "long, int"); }
  public void testSimpleArr2() { doSimpleTest("<selection>firstArg[0]</selection><caret>", "long"); }
  public void testSimpleFun1() { doSimpleTest("f(firstArg, 12)<caret>", "int, int"); }
  public void testSimpleFun2() { doSimpleTest("<selection>f(firstArg, 12)</selection><caret>", "int"); }
  public void testSimpleBad1() { doSimpleTest("something_missing<caret>", "&lt;unknown&gt;, int"); }

  private void doSimpleTest(@NotNull String param, @NotNull String expected) {
    doTest("class A { int f(long[] firstArg, Object s) { f(p, " + param + ");}}", expected);
  }

  private void doTest(@NotNull String text, @NotNull String expected) {
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    ShowExpressionTypeHandler handler = new ShowExpressionTypeHandler(false);
    Map<PsiElement, ExpressionTypeProvider> map =
      handler.getExpressions(myFixture.getFile(), myFixture.getEditor());
    //noinspection unchecked
    assertEquals(expected, StringUtil.join(map.entrySet(), o -> o.getValue().getInformationHint(o.getKey()), ", "));
  }
}
