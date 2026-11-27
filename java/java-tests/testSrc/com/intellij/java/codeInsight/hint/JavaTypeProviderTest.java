// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.hint;

import com.intellij.codeInsight.EditorInfo;
import com.intellij.codeInsight.hint.JavaTypeProvider;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.idea.TestFor;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiKeyword;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("HtmlDeprecatedAttribute")
@TestApplication
public class JavaTypeProviderTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());

  @Test
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
  
  @Test
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
  
  @Test
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

  @TestFor(issues = "IDEA-381645")
  @Test
  public void testGenericNoHint() {
    doTest("""
             void test(java.util.Optional<java.util.List<String>> opt) {
                 System.out.println(<selection>opt</selection>);
             }
             }}""", "Optional&lt;List&lt;String&gt;&gt;", 
           "Optional&lt;List&lt;String&gt;&gt;");
  }
  
  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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
  
  @Test
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
  
  @Test
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

  @Test
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
  
  @Test
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

  @Test
  public void testVarIdentifier() {
    doTest("""
             void test() {
               var <selection>x</selection> = 1;
             }""", "int",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>int</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Value:</td><td>1</td></tr>" +
           "</table>");
  }

  @Test
  public void testVarKeyword() {
    doTest("""
             void test() {
               <selection>var</selection> x = 1;
             }""", "int",
           "<table>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Type:</td><td>int</td></tr>" +
           "<tr><td align=\"left\" style=\"color:#909090\" valign=\"top\">Value:</td><td>1</td></tr>" +
           "</table>");
  }
  @Test
  public void testLambdaParameter() {
    doTest("""
             void test() {
                        interface Consumer<T>{
                          public void consume(T t);
                        }
                        class Test2{
                                     public void test(Consumer<String> a) {
                     
                                     }
                                 }
                                 new Test2().test(<selection>a</selection>-> System.out.println(a));
             }""", "String", "String");
  }

  @Test
  public void testLambdaParameter2() {
    doTest("""
             void test() {
                        interface Consumer<T>{
                          public void consume(T t);
                        }
                        class Test2{
                                     public void test(Consumer<String> a) {
                     
                                     }
                                 }
                                 new Test2().test((<selection>var a</selection>)-> System.out.println(a));
             }""", "String", "String");
  }
  @Test
  public void testDeconstructionVariable() {
    doTest("""
             void test(Object x) {
                switch (x) {
                    record Record(String a) {
                
                    }
                
                    case Record(<selection>var a</selection>) -> {
                        System.out.println(a);
                    }
                    default -> {
                        System.out.println(x);
                    }
                }
             }""", "String", "String");
  }

  @Test
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

  private static void doTest(@Language(value = "JAVA", prefix = "@SuppressWarnings(\"all\")class X{", suffix = "}") String method,
                             @Language("HTML") String expectedHint,
                             @Language("HTML") String expectedAdvancedHint) {
    EditorInfo info = new EditorInfo("class X{" + method + "}");
    assertEquals(1, info.caretState.carets().size(), "Single selection must be specified");
    TextRange selection = info.caretState.carets().get(0).selection;
    assertNotNull(selection, "No <selection>..</selection> in test data");
    ReadAction.runBlocking(() -> {
      PsiFile file =
        PsiFileFactory.getInstance(projectFixture.get().getProject()).createFileFromText("X.java", JavaFileType.INSTANCE, info.getNewFileText());
      PsiElement element =
        PsiTreeUtil.findElementOfClassAtRange(file, selection.getStartOffset(), selection.getEndOffset(), PsiExpression.class);
      if (element == null) {
        element =
          PsiTreeUtil.findElementOfClassAtRange(file, selection.getStartOffset(), selection.getEndOffset(), PsiParameter.class);
      }
      if (element == null) {
        element =
          PsiTreeUtil.findElementOfClassAtRange(file, selection.getStartOffset(), selection.getEndOffset(), PsiIdentifier.class);
      }
      if (element == null) {
        element =
          PsiTreeUtil.findElementOfClassAtRange(file, selection.getStartOffset(), selection.getEndOffset(), PsiKeyword.class);
      }
      assertNotNull(element, "Expression not found");
      JavaTypeProvider provider = new JavaTypeProvider();
      assertEquals(expectedHint, provider.getInformationHint(element));
      assertTrue(provider.hasAdvancedInformation());
      assertEquals(expectedAdvancedHint, provider.getAdvancedInformationHint(element));
    });
  }
}
