// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.editorActions;

import com.intellij.codeInsight.editorActions.AbstractMoveElementLeftRightTest;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.testFramework.junit5.TestApplication;
import org.junit.jupiter.api.Test;

@TestApplication
public class MoveElementLeftRightTest extends AbstractMoveElementLeftRightTest {
  @Test
  public void testMoveLiteralMethodParameter() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m() { System.setProperty(<caret>\"a\", \"b\"); } }", 
                            "class C { void m() { System.setProperty(\"b\", <caret>\"a\"); } }");
    });
  }
  
  @Test
  public void testMoveAtLiteralMethodParameterEnd() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m() { System.setProperty(\"a\"<caret>, \"b\"); } }", 
                            "class C { void m() { System.setProperty(\"b\", \"a\"<caret>); } }");
    });
  }
  
  @Test
  public void testMoveComplexMethodParameter() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m() { System.setProperty(<caret>System.getEnv(null), \"b\"); } }", 
                            "class C { void m() { System.setProperty(\"b\", <caret>System.getEnv(null)); } }");
    });
  }
  
  @Test
  public void testMoveOuterLevelOutsideParenthesis() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m() { System.setProperty(System.setProperty<caret>(\"c\", \"d\"), \"b\"); } }", 
                            "class C { void m() { System.setProperty(\"b\", System.setProperty<caret>(\"c\", \"d\")); } }");
    });
  }
  
  @Test
  public void testMoveInnerLevelInsideParenthesis() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m() { System.setProperty(System.setProperty(<caret>\"c\", \"d\"), \"b\"); } }",
                            "class C { void m() { System.setProperty(System.setProperty(\"d\", <caret>\"c\"), \"b\"); } }");
    });
  }
  
  @Test
  public void testMoveOuterLevelInsideParenthesisWithSingleParameter() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m() { System.setProperty(System.getEnv(<caret>null), \"b\"); } }",
                            "class C { void m() { System.setProperty(\"b\", System.getEnv(<caret>null)); } }");
    });
  }

  @Test
  public void testMoveSingleParameterWithSelection() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m() { System.setProperty(\"<selection>a<caret></selection>\", \"b\"); } }",
                            "class C { void m() { System.setProperty(\"b\", \"<selection>a<caret></selection>\"); } }");
    });
  }
  
  @Test
  public void testMoveTwoParametersWithSelection() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m() { java.util.Arrays.asList(\"<selection><caret>a\", \"b</selection>\", \"c\"); } }",
                            "class C { void m() { java.util.Arrays.asList(\"c\", \"<selection><caret>a\", \"b</selection>\"); } }");
    });
  }

  @Test
  public void testMoveTwoParametersWithMultiCaret() {
    myFixture.runTest(() -> {
      doTestSingle(false,
                   "class C { void m() { java.util.Arrays.asList(\"<caret>a\", \"<caret>b\", \"c\"); } }",
                   "class C { void m() { java.util.Arrays.asList(\"c\", \"<caret>a\", \"<caret>b\"); } }");
    });
  }
  
  @Test
  public void testMovingMethodDeclaredParameters() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m(i<caret>nt a, String b, @NotNull List c) {} }",
                            "class C { void m(String b, i<caret>nt a, @NotNull List c) {} }",
                            "class C { void m(String b, @NotNull List c, i<caret>nt a) {} }");
    });
  }
  
  @Test
  public void testMovingArrayInitializerComponents() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { int[] a = {1<caret>, 2, 3}; }",
                            "class C { int[] a = {2, 1<caret>, 3}; }",
                            "class C { int[] a = {2, 3, 1<caret>}; }");
    });
  }
  
  @Test
  public void testMoveEnumConstants() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("enum E { AA<caret>, BB, }",
                            "enum E { BB, AA<caret>, }");
    });
  }
  
  @Test
  public void testMoveAnnotationParameters() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("@SomeAnnotation(p1=<caret>1, p2 = \"2\") class C {}",
                            "@SomeAnnotation(p2 = \"2\", p1=<caret>1) class C {}");
    });
  }

  @Test
  public void testMoveThrowsExceptions() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m() throws RuntimeExceptio<caret>n, Exception {} }",
                            "class C { void m() throws Exception, RuntimeExceptio<caret>n {} }");
    });
  }

  @Test
  public void testMoveThrowsExceptionsWithCaretAtEnd() {
    myFixture.runTest(() -> {
      doTestFromRightToLeft("class C { void m() throws RuntimeException, Exception<caret> {} }",
                            "class C { void m() throws Exception<caret>, RuntimeException {} }");
    });
  }

  @Test
  public void testMoveImplementsClause() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C implements Cl<caret>oneable, java.io.Serializable {}",
                            "class C implements java.io.Serializable, Cl<caret>oneable {}");
    });
  }

  @Test
  public void testMoveMulticatch() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m() { try {} catch (Runtime<caret>Exception | Exception e) {}",
                            "class C { void m() { try {} catch (Exception | Runtime<caret>Exception e) {}");
    });
  }

  @Test
  public void testMoveTryResource() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { void m(AutoCloseable a) { try (Auto<caret>Closeable b = a; AutoCloseable c = null) {} } }",
                            "class C { void m(AutoCloseable a) { try (AutoCloseable c = null; Auto<caret>Closeable b = a) {} } }");
    });
  }

  @Test
  public void testMovePolyadicExpressionOperand() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C { int i = <caret>1 + 2 + 3; }",
                            "class C { int i = 2 + <caret>1 + 3; }",
                            "class C { int i = 2 + 3 + <caret>1; }");
    });
  }

  @Test
  public void testMoveTypeParameter() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C {{ java.util.Map<Object<caret>, String> map; }}",
                            "class C {{ java.util.Map<String, Object<caret>> map; }}");
    });
  }

  @Test
  public void testMoveClassTypeParameter() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("class C<<caret>A, B> {}",
                            "class C<B, <caret>A> {}");
    });
  }

  @Test
  public void testMoveModifier() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("@Suppress<caret>Warnings(\"ALL\") final class C {}",
                            "final @Suppress<caret>Warnings(\"ALL\") class C {}");
    });
  }

  @Test
  public void testMoveAnnotationMemberValue() {
    myFixture.runTest(() -> {
      doTestFromLeftToRight("@SuppressWarnings({\"<caret>a\", \"b\"}) final class C {}",
                            "@SuppressWarnings({\"b\", \"<caret>a\"}) final class C {}");
    });
  }

  @Override
  protected void configureEditor(String contents) {
    myFixture.init(contents, JavaFileType.INSTANCE);
  }
}
