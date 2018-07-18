// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.codeInsight.EditorInfo;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

public class JavaTypeProviderTest extends LightCodeInsightTestCase {
  public void testRangeHint() {
    doTest("  void test(int x) {\n" +
           "    x = Math.abs(x);\n" +
           "    <selection>x</selection>\n" +
           "  }", "int",
           "<table>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Type:</td><td>int</td></tr>" +
           "<tr><td align='left' valign='top' style='color:#909090'>Range:</td><td>{Integer.MIN_VALUE, 0..Integer.MAX_VALUE}</td></tr>" +
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
           "<tr><td align='left' valign='top' style='color:#909090'>Optional:</td><td>present Optional</td></tr>" +
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

  private static void doTest(@Language(value = "JAVA", prefix = "@SuppressWarnings(\"all\")class X{", suffix = "}") String method,
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