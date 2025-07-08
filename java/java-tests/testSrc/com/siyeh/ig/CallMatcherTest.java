// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.CommonClassNames.*;

public class CallMatcherTest extends LightJavaCodeInsightFixtureTestCase {

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  public void testUnresolvedPartiallyMatchQualifier() {
    @Language("JAVA") String text = """
      class Main{
        void m() {
          IO.<caret>println();
        }
      }
      """;
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(0)
                      .allowUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "print", "println")
                      .parameterCount(0)
                      .allowUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "print")
                      .parameterCount(0)
                      .allowUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(1)
                      .allowUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "print")
                      .parameterCount(0)
                      .allowUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.NIO", "println")
                      .parameterCount(0)
                      .allowUnresolved(), text));


    @Language("JAVA") String textWithFullyQualifiedName = """
      class Main{
        void m() {
          java.lang.IO.<caret>println();
        }
      }
      """;
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(0)
                      .allowUnresolved(), textWithFullyQualifiedName));

    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.NIO", "println")
                      .parameterCount(0)
                      .allowUnresolved(), textWithFullyQualifiedName));

  }


  public void testUnresolvedWithParameters() {
    @Language("JAVA") String text = """
      class Main{
        void m() {
          java.lang.IO.<caret>println("test");
        }
      }
      """;
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(0)
                      .allowUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(1)
                      .allowUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(2)
                      .allowUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterTypes(JAVA_LANG_STRING)
                      .allowUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING)
                      .allowUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterTypes(JAVA_LANG_INTEGER)
                      .allowUnresolved(), text));

    @Language("JAVA") String textParameters2 = """
      class Main{
        void m() {
          java.lang.IO.<caret>println("test", "test2");
        }
      }
      """;

    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(0)
                      .allowUnresolved(), textParameters2));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(1)
                      .allowUnresolved(), textParameters2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(2)
                      .allowUnresolved(), textParameters2));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(3)
                      .allowUnresolved(), textParameters2));
  }


  public void testUnresolvedWithVarArgs() {
    //imagine that there is "java.lang.IO2" with "printf"
    @Language("JAVA") String text = """
      class Main{
        void m() {
          java.lang.IO2.<caret>printf("test");
        }
      }
      """;
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterCount(1)
                      .allowUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING + "...")
                      .allowUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_OBJECT + "...")
                      .allowUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_OBJECT + "...")
                      .allowUnresolved(), text));


    @Language("JAVA") String textWithVarArgs2 = """
      class Main{
        void m() {
          java.lang.IO2.<caret>printf("test", "a", "b");
        }
      }
      """;

    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_OBJECT + "...")
                      .allowUnresolved(), textWithVarArgs2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING + "...")
                      .allowUnresolved(), textWithVarArgs2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING + "...")
                      .allowUnresolved(), textWithVarArgs2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING + "...")
                      .allowUnresolved(), textWithVarArgs2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_OBJECT + "...")
                      .allowUnresolved(), textWithVarArgs2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_INTEGER + "...")
                      .allowUnresolved(), textWithVarArgs2));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_INTEGER + "...")
                      .allowUnresolved(), textWithVarArgs2));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING + "...")
                      .allowUnresolved(), textWithVarArgs2));
  }

  private boolean isMatchedCall(@NotNull CallMatcher matcher, @Language("JAVA") @NotNull String text) {
    PsiFile file = myFixture.configureByText("Main.java", text);
    PsiElement element = file.findElementAt(myFixture.getCaretOffset());
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
    return matcher.matches(callExpression);
  }
}
