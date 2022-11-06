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
    doTest("""
               void test(int x) {
                 x = Math.abs(x);
                 <selection>x</selection>
               }\
             """, "int",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>int</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Range:</td><td>Integer.MIN_VALUE or &gt;= 0</td></tr>" +
           "</table>");
  }
  
  public void testFloatRangeHint() {
    doTest("void test(double x) {" +
           "if (x > 0.5 && x < 1.8) {" +
           "<selection>x</selection>" +
           "}}", "double",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>double</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Range:</td><td>&gt; 0.5 &amp;&amp; &lt; 1.8 not NaN</td></tr>" +
           "</table>");
  }
  
  public void testFloatRangeHint2() {
    doTest("void test(double x) {" +
           "if (!(x > 0.5 && x < 1.8)) {" +
           "<selection>x</selection>" +
           "}}", "double",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>double</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Range:</td><td>&lt;= 0.5 || &gt;= 1.8 (or NaN)</td></tr>" +
           "</table>");
  }
  
  public void testFloatConstantHint() {
    doTest("void test(double x) {" +
           "if (x == 1.0) {" +
           "<selection>x</selection>" +
           "}", "double",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>double</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Value:</td><td>1.0</td></tr>" +
           "</table>");
  }

  public void testOptionalHint() {
    doTest("""
               void test(java.util.Optional<String> t) {
                 if(t.isPresent()) {
                   <selection>t</selection>
                 }
               }\
             """, "Optional&lt;String&gt;",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>Optional&lt;String&gt;</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Nullability:</td><td>non-null</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Optional value:</td><td>present Optional</td></tr>" +
           "</table>");
  }

  public void testFunctionalType() {
    doTest("""
               void test() {
                   Runnable r = <selection>() -> {}</selection>;
               }\
             """, "Runnable",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>Runnable</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Nullability:</td><td>non-null</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Constraints:</td><td>exactly ? extends Runnable</td></tr>" +
           "</table>");
  }

  public void testTypeConstraint() {
    doTest("""
               void x(Object a) {
                 if(a instanceof String || a instanceof Number) {
                  \s
                 } else if(a instanceof CharSequence){
                   <selection>a</selection>
                 } else {
                   a
                 }
               }\
             """, "Object",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>Object</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Nullability:</td><td>non-null</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Constraints:</td><td>instanceof CharSequence<br/>" +
           "not instanceof Number, String</td></tr>" +
           "</table>");
  }

  public void testTypeConstraint2() {
    doTest("""
               void x(Object a) {
                 if(a instanceof String || a instanceof Number) {
                  \s
                 } else if(a instanceof CharSequence){
                  \s
                 } else {
                   <selection>a</selection>
                 }
               }
             """, "Object",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>Object</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Constraints:</td><td>not instanceof CharSequence, Number</td></tr>" +
           "</table>");
  }

  public void testSpecialField() {
    doTest("""
             void x(int[] data) {
               if (data.length == 1) {
                 System.out.println(java.util.Arrays.toString(<selection>data</selection>));
               }
             }""", "int[]",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>int[]</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Nullability:</td><td>non-null</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Array length:</td><td>1</td></tr>" +
           "</table>");
  }
  
  public void testBooleanValue() {
    doTest("""
             void test(int x) {
               if(x > 5 && <selection>x < 0</selection>) {}
             }""", "boolean",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>boolean</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Value:</td><td>false</td></tr>" +
           "</table>");
  }
  
  public void testStringValue() {
    doTest("""
             void test(String x) {
               if(x.equals("foo") || x.equals("bar")) {
                 <selection>x</selection>
               }
             }""", "String",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>String</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Value:</td><td>&quot;bar&quot; or &quot;foo&quot;</td></tr>" +
           "</table>");
  }
  
  public void testNotValue() {
    doTest("""
             void test(String x) {
               if(x.equals("foo") || x.equals("bar")) {} else {
                 <selection>x</selection>
               }
             }""", "String",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>String</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Not equal to:</td><td>&quot;bar&quot;, &quot;foo&quot;</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Nullability:</td><td>non-null</td></tr>" +
           "</table>");
  }
  
  public void testNotValueEnum() {
    doTest("""
             enum X {A, B}void test(X x) {
               if(x == X.A) {} else {
                 <selection>x</selection>
               }
             }""", "X",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>X</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Not equal to:</td><td>X.A</td></tr>" +
           "</table>");
  }
  
  public void testEscaping() {
    doTest("""
             public static void main(int i) {
               if (i < 50) {
                 System.out.println(<selection>i</selection>);
               }
             }""", "int",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>int</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Range:</td><td>&lt;= 49</td></tr>" +
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