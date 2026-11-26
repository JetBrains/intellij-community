// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight;

import com.intellij.codeInsight.hint.ShowExpressionTypeHandler;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.idea.TestFor;
import com.intellij.lang.ExpressionTypeProvider;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.intellij.testFramework.JavaCodeInsightFixtureKt.javaModuleCodeInsightFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gregsh
 */
@TestApplication
public class ExpressionTypeActionTest {
  private final TestFixture<JavaCodeInsightTestFixture> codeInsightFixture = javaModuleCodeInsightFixture(PathManager.getHomePath());

  @Test
  public void testSimpleStr1() { doSimpleTest("\"\"<caret>", "String, int"); }
  @Test
  public void testSimpleStr2() { doSimpleTest("<selection>\"\"</selection><caret>", "String"); }
  @Test
  public void testSimpleStr3() { doSimpleTest("\"\"<caret>,", "String, int"); }
  @Test
  public void testSimpleStr4() { doSimpleTest("\"\"<caret>\n", "String, int"); }
  @Test
  public void testSimpleChr1() { doSimpleTest("\"\" + 'x'<caret>", "char, String, int"); }
  @Test
  public void testSimpleChr2() { doSimpleTest("\"\" + <selection>'x'</selection><caret>", "char"); }
  @Test
  public void testSimpleInt1() { doSimpleTest("111<caret>", "int, int"); }
  @Test
  public void testSimpleInt2() { doSimpleTest("<selection>111</selection><caret>", "int"); }
  @Test
  public void testSimpleArg1() { doSimpleTest("firstArg<caret>", "long[], int"); }
  @Test
  public void testSimpleArg2() { doSimpleTest("<selection>firstArg</selection><caret>", "long[]"); }
  @Test
  public void testSimpleArr1() { doSimpleTest("firstArg[0]<caret>", "long, int"); }
  @Test
  public void testSimpleArr2() { doSimpleTest("<selection>firstArg[0]</selection><caret>", "long"); }
  @Test
  public void testSimpleFun1() { doSimpleTest("f(firstArg, 12)<caret>", "int, int"); }
  @Test
  public void testSimpleFun2() { doSimpleTest("<selection>f(firstArg, 12)</selection><caret>", "int"); }
  @Test
  public void testSimpleBad1() { doSimpleTest("something_missing<caret>", "&lt;unknown&gt;, int"); }

  @TestFor(issues = "IJPL-248690")
  @Test
  public void testSelectionWithTrailingWhitespace() { doStatementTest("<selection>111 </selection><caret>", "int"); }

  @TestFor(issues = "IJPL-248690")
  @Test
  public void testSelectionWithLeadingWhitespace() { doStatementTest("<selection> 111</selection><caret>", "int"); }

  @TestFor(issues = "IJPL-248690")
  @Test
  public void testSelectionWithSurroundingWhitespace() { doStatementTest("<selection> 111 </selection><caret>", "int"); }

  private void doSimpleTest(@NotNull String param, @NotNull String expected) {
    doTest("class A { int f(long[] firstArg, Object s) { f(p, " + param + ");}}", expected);
  }

  private void doStatementTest(@NotNull String param, @NotNull String expected) {
    // a statement context, unlike the f(p, ...) wrapper above, has no enclosing expression to absorb the
    // surrounding whitespace, reproducing the original "No expression found" scenario from the issue
    doTest("class A { void m() { int x = " + param + "; } }", expected);
  }

  private void doTest(@NotNull String text, @NotNull String expected) {
    JavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText(JavaFileType.INSTANCE, text);
    String actual = ReadAction.computeBlocking(() -> {
      ShowExpressionTypeHandler handler = new ShowExpressionTypeHandler(false);
      Map<PsiElement, ExpressionTypeProvider> map =
        handler.getExpressions(myFixture.getFile(), myFixture.getEditor());
      //noinspection unchecked
      return StringUtil.join(map.entrySet(), o -> o.getValue().getInformationHint(o.getKey()), ", ");
    });
    assertEquals(expected, actual);
  }
}
