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
                      .allowStaticUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "print", "println")
                      .parameterCount(0)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "print")
                      .parameterCount(0)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "print")
                      .parameterCount(0)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.NIO", "println")
                      .parameterCount(0)
                      .allowStaticUnresolved(), text));


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
                      .allowStaticUnresolved(), textWithFullyQualifiedName));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang2.IO", "println")
                      .parameterCount(0)
                      .allowStaticUnresolved(), textWithFullyQualifiedName));

    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.NIO", "println")
                      .parameterCount(0)
                      .allowStaticUnresolved(), textWithFullyQualifiedName));


    @Language("JAVA") String textWithEmptyFullyQualifiedName = """
      class Main{
        void m() {
          <caret>println();
        }
      }
      """;
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("", "println")
                      .parameterCount(0)
                      .allowStaticUnresolved(), textWithEmptyFullyQualifiedName));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(0)
                      .allowStaticUnresolved(), textWithEmptyFullyQualifiedName));
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
                      .allowStaticUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(2)
                      .allowStaticUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterTypes(JAVA_LANG_STRING)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterTypes(JAVA_LANG_INTEGER)
                      .allowStaticUnresolved(), text));

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
                      .allowStaticUnresolved(), textParameters2));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(1)
                      .allowStaticUnresolved(), textParameters2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(2)
                      .allowStaticUnresolved(), textParameters2));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO", "println")
                      .parameterCount(3)
                      .allowStaticUnresolved(), textParameters2));
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
                      .allowStaticUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING + "...")
                      .allowStaticUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_OBJECT + "...")
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_OBJECT + "...")
                      .allowStaticUnresolved(), text));


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
                      .allowStaticUnresolved(), textWithVarArgs2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING + "...")
                      .allowStaticUnresolved(), textWithVarArgs2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING + "...")
                      .allowStaticUnresolved(), textWithVarArgs2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING + "...")
                      .allowStaticUnresolved(), textWithVarArgs2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_OBJECT + "...")
                      .allowStaticUnresolved(), textWithVarArgs2));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_INTEGER + "...")
                      .allowStaticUnresolved(), textWithVarArgs2));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_INTEGER + "...")
                      .allowStaticUnresolved(), textWithVarArgs2));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("java.lang.IO2", "printf")
                      .parameterTypes(JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING, JAVA_LANG_STRING + "...")
                      .allowStaticUnresolved(), textWithVarArgs2));
  }

  public void testUnresolvedSamePackage() {
    @Language("JAVA") String text = """
      package foo.bar;
      class Main{
        void m() {
          IO2.<caret>printf("test");
        }
      }
      """;
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar2.IO2", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));

  }

  public void testUnresolvedOnDemandImports() {
    @Language("JAVA") String text = """
      import static foo.bar.IO2.*;
      package foo.bar;
      class Main{
        void m() {
          <caret>printf("test");
        }
      }
      """;
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar2.IO2", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));

    @Language("JAVA") String textNested = """
      import static foo.bar.IO2.*;
      package foo.bar;
      class Main{
        void m() {
          IO.<caret>printf("test");
        }
      }
      """;
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2.IO", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), textNested));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2.ION", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), textNested));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), textNested));
  }

  public void testUnresolvedImports() {
    @Language("JAVA") String text = """
      import static foo.bar.IO2;
      package foo.bar;
      class Main{
        void m() {
          IO2.<caret>printf("test");
        }
      }
      """;
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar2.IO2", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), text));


    @Language("JAVA") String textNested = """
      import static foo.bar.IO2;
      package foo.bar;
      class Main{
        void m() {
          IO2.IO.<caret>printf("test");
        }
      }
      """;
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2.IO", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), textNested));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), textNested));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), textNested));
    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2.IO.I", "printf")
                      .parameterCount(1)
                      .allowStaticUnresolved(), textNested));
  }

  public void testUnresolvedWithArgs() {
    @Language("JAVA") String text = """
      import static foo.bar.IO2;
      package foo.bar;
      class Main{
        void m() {
          IO2.<caret>printf("a", new String[]{"test"});
        }
      }
      """;

    assertTrue(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2", "printf")
                      .parameterCount(2)
                      .allowStaticUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2", "printf")
                      .parameterTypes("java.lang.String", "java.lang.String[]")
                      .allowStaticUnresolved(), text));
    assertTrue(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2", "printf")
                      .parameterTypes("java.lang.String", "java.lang.String...")
                      .allowStaticUnresolved(), text));


    @Language("JAVA") String textWithArray = """
      import static foo.bar.IO2;
      package foo.bar;
      class Main{
        void m() {
          IO2.<caret>printf("a", new String[]{"test"}, "a");
        }
      }
      """;

    assertTrue(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2", "printf")
                      .parameterTypes("java.lang.String", "java.lang.String[]", "java.lang.String")
                      .allowStaticUnresolved(), textWithArray));

    assertFalse(
      isMatchedCall(CallMatcher.staticCall("foo.bar.IO2", "printf")
                      .parameterTypes("java.lang.String", "java.lang.String...")
                      .allowStaticUnresolved(), textWithArray));
  }

  private boolean isMatchedCall(@NotNull CallMatcher matcher, @Language("JAVA") @NotNull String text) {
    PsiFile file = myFixture.configureByText("Main.java", text);
    PsiElement element = file.findElementAt(myFixture.getCaretOffset());
    PsiMethodCallExpression callExpression = PsiTreeUtil.getParentOfType(element, PsiMethodCallExpression.class, false);
    return matcher.matches(callExpression);
  }
}
