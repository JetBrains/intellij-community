// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.psi.*;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.intellij.lang.annotations.Language;

public class JavaTypeProviderTest extends LightCodeInsightTestCase {
  public void testRangeHint() {
    doTest("  void test(int x) {\n" +
           "    x = Math.abs(x);\n" +
           "    /*expression*/x\n" +
           "  }", "int",
           "<table>" +
           "<tr><td align='right' valign='top'><strong>Type:</strong></td>" +
           "<td>int</td></tr>" +
           "<tr><td align='right' valign='top'><strong>Range:</strong></td>" +
           "<td>{Integer.MIN_VALUE, 0..Integer.MAX_VALUE}</td></tr>" +
           "</table>");
  }

  public void testOptionalHint() {
    doTest("  void test(java.util.Optional<String> t) {\n" +
           "    if(t.isPresent()) {\n" +
           "      /*expression*/t\n" +
           "    }\n" +
           "  }", "Optional&lt;String&gt;",
           "<table>" +
           "<tr><td align='right' valign='top'><strong>Type:</strong></td>" +
           "<td>Optional&lt;String&gt;</td></tr>" +
           "<tr><td align='right' valign='top'><strong>Nullability:</strong></td>" +
           "<td>NotNull</td></tr>" +
           "<tr><td align='right' valign='top'><strong>Optional presense:</strong></td>" +
           "<td>present Optional</td></tr>" +
           "</table>");
  }

  public void testTypeConstraint() {
    doTest("  void x(Object a) {\n" +
           "    if(a instanceof String || a instanceof Number) {\n" +
           "      \n" +
           "    } else if(a instanceof CharSequence){\n" +
           "      /*expression*/a\n" +
           "    } else {\n" +
           "      a\n" +
           "    }\n" +
           "  }", "Object",
           "<table>" +
           "<tr><td align='right' valign='top'><strong>Type:</strong></td>" +
           "<td>Object</td></tr>" +
           "<tr><td align='right' valign='top'><strong>Nullability:</strong></td>" +
           "<td>NotNull</td></tr>" +
           "<tr><td align='right' valign='top'><strong>Type constraints:</strong></td>" +
           "<td>instanceof CharSequence\n" +
           "not instanceof Number, String</td></tr></table>");
  }

  public void testTypeConstraint2() {
    doTest("  void x(Object a) {\n" +
           "    if(a instanceof String || a instanceof Number) {\n" +
           "      \n" +
           "    } else if(a instanceof CharSequence){\n" +
           "      \n" +
           "    } else {\n" +
           "      /*expression*/a\n" +
           "    }\n" +
           "  }\n", "Object",
           "<table>" +
           "<tr><td align='right' valign='top'><strong>Type:</strong></td>" +
           "<td>Object</td></tr>" +
           "<tr><td align='right' valign='top'><strong>Type constraints:</strong></td>" +
           "<td>not instanceof CharSequence, Number</td></tr></table>");
  }

  private static void doTest(@Language(value = "JAVA", prefix = "@SuppressWarnings(\"all\")class X{", suffix = "}") String method,
                             @Language("HTML") String expectedHint,
                             @Language("HTML") String expectedAdvancedHint) {
    PsiFile file =
      PsiFileFactory.getInstance(getProject()).createFileFromText("X.java", JavaFileType.INSTANCE, "class X{" + method + "}");
    PsiComment comment = SyntaxTraverser.psiTraverser(file).filter(PsiComment.class)
                                        .filter(c -> c.textMatches("/*expression*/"))
                                        .first();
    assertNotNull("/*expression*/ comment not found", comment);
    PsiElement sibling = comment.getNextSibling();
    assertNotNull(sibling);
    while (!(sibling instanceof PsiExpression) && sibling.getFirstChild() != null) {
      sibling = sibling.getFirstChild();
    }
    assertTrue("Expression not found at: " + sibling.getText(), sibling instanceof PsiExpression);
    PsiExpression expression = (PsiExpression)sibling;
    JavaTypeProvider provider = new JavaTypeProvider();
    assertEquals(expectedHint, provider.getInformationHint(expression));
    assertTrue(provider.hasAdvancedInformation());
    assertEquals(expectedAdvancedHint, provider.getAdvancedInformationHint(expression));
  }
}