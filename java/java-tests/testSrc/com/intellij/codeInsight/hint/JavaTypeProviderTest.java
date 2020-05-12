// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.EditorInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

@SuppressWarnings("HtmlDeprecatedAttribute")
public class JavaTypeProviderTest extends LightJavaCodeInsightTestCase {
  public void testRangeHint() {
    doTest("  void test(int x) {\n" +
           "    x = Math.abs(x);\n" +
           "    <selection>x</selection>\n" +
           "  }", "int",
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>int</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Range:</td><td>Integer.MIN_VALUE or >= 0</td></tr>" +
           "</table>");
  }

  public void testOptionalHint() {
    doTest("  void test(java.util.Optional<String> t) {\n" +
           "    if(t.isPresent()) {\n" +
           "      <selection>t</selection>\n" +
           "    }\n" +
           "  }", "Optional&lt;String&gt;",
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>Optional&lt;String&gt;</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Nullability:</td><td>non-null</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Value:</td><td>present Optional</td></tr>" +
           "</table>");
  }

  public void testFunctionalType() {
    doTest("  void test() {\n" +
           "      Runnable r = <selection>() -> {}</selection>;\n" +
           "  }", "Runnable", 
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>Runnable</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Nullability:</td><td>non-null</td></tr>" +
           "</table>");
  }

  public void testTypeConstraint() {
    doTest("  void x(Object a) {\n" +
           "    if(a instanceof String || a instanceof Number) {\n" +
           "      \n" +
           "    } else if(a instanceof CharSequence){\n" +
           "      <selection>a</selection>\n" +
           "    } else {\n" +
           "      a\n" +
           "    }\n" +
           "  }", "Object",
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>Object</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Nullability:</td><td>non-null</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Constraints:</td><td>instanceof CharSequence\n" +
           "not instanceof Number, String</td></tr>" +
           "</table>");
  }

  public void testTypeConstraint2() {
    doTest("  void x(Object a) {\n" +
           "    if(a instanceof String || a instanceof Number) {\n" +
           "      \n" +
           "    } else if(a instanceof CharSequence){\n" +
           "      \n" +
           "    } else {\n" +
           "      <selection>a</selection>\n" +
           "    }\n" +
           "  }\n", "Object",
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>Object</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Constraints:</td><td>not instanceof CharSequence, Number</td></tr>" +
           "</table>");
  }

  public void testSpecialField() {
    doTest("void x(int[] data) {\n" +
           "  if (data.length == 1) {\n" +
           "    System.out.println(java.util.Arrays.toString(<selection>data</selection>));\n" +
           "  }\n" +
           "}", "int[]",
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>int[]</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Nullability:</td><td>non-null</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Length:</td><td>1</td></tr>" +
           "</table>");
  }
  
  public void testBooleanValue() {
    doTest("void test(int x) {\n" +
           "  if(x > 5 && <selection>x < 0</selection>) {}\n" +
           "}", "boolean", 
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>boolean</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Value:</td><td>false</td></tr>" +
           "</table>");
  }
  
  public void testStringValue() {
    doTest("void test(String x) {\n" +
           "  if(x.equals(\"foo\") || x.equals(\"bar\")) {\n" +
           "    <selection>x</selection>\n" +
           "  }\n" +
           "}", "String", 
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>String</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Value (one of):</td><td>\"bar\", \"foo\"</td></tr>" +
           "</table>");
  }
  
  public void testNotValue() {
    doTest("void test(String x) {\n" +
           "  if(x.equals(\"foo\") || x.equals(\"bar\")) {} else {\n" +
           "    <selection>x</selection>\n" +
           "  }\n" +
           "}", "String", 
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>String</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Not equal to:</td><td>\"bar\", \"foo\"</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Nullability:</td><td>non-null</td></tr>" +
           "</table>");
  }
  
  public void testNotValueEnum() {
    doTest("enum X {A, B}" +
           "void test(X x) {\n" +
           "  if(x == X.A) {} else {\n" +
           "    <selection>x</selection>\n" +
           "  }\n" +
           "}", "X", 
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>X</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Not equal to:</td><td>X.A</td></tr>" +
           "</table>");
  }

  private void doTest(@Language(value = "JAVA", prefix = "@SuppressWarnings(\"all\")class X{", suffix = "}") String method,
                             @Language("HTML") String expectedHint,
                             @Language("HTML") String expectedAdvancedHint) {
    EditorInfo info = new EditorInfo("class X{" + method + "}");
    PsiFile file = PsiFileFactory.getInstance(getProject()).createFileFromText("X.java", JavaFileType.INSTANCE, info.getNewFileText());
    assertEquals("Single selection must be specified", 1, info.caretState.carets.size());
    TextRange selection = info.caretState.carets.get(0).selection;
    assertNotNull("No <selection>..</selection> in test data", selection);
    PsiExpression expression =
      PsiTreeUtil.findElementOfClassAtRange(file, selection.getStartOffset(), selection.getEndOffset(), PsiExpression.class);
    assertNotNull("Expression not found", expression);
    JavaTypeProvider provider = new JavaTypeProvider();
    assertEquals(expectedHint, provider.getInformationHint(expression));
    assertTrue(provider.hasAdvancedInformation());
    assertEquals(expectedAdvancedHint, provider.getAdvancedInformationHint(expression));
  }
}