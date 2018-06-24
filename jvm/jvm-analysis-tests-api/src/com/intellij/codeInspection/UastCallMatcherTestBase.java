package com.intellij.codeInspection;

import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestDataFile;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UCallableReferenceExpression;

import java.util.Locale;
import java.util.Set;

import static com.intellij.codeInspection.JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile;
import static com.intellij.codeInspection.UastCallMatcher.builder;

public abstract class UastCallMatcherTestBase extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
    moduleBuilder.addLibrary("javaUtil", PathUtil.getJarPathForClass(Locale.class));
  }

  protected static int matchCallExpression(UastCallMatcher matcher, Set<UCallExpression> expressions) {
    return (int)expressions.stream().filter(matcher::testCallExpression).count();
  }

  protected static int matchCallableReferenceExpression(UastCallMatcher matcher, Set<UCallableReferenceExpression> expressions) {
    return (int)expressions.stream().filter(matcher::testCallableReferenceExpression).count();
  }

  // expected matched expressions count are the same for Kotlin and Java tests for callable reference expressions
  protected void doTestCallableReferences(@TestDataFile @NotNull String file) {
    PsiFile psiFile = myFixture.configureByFile(file);
    Set<UCallableReferenceExpression> expressions = getUElementsOfTypeFromFile(psiFile, UCallableReferenceExpression.class);
    assertSize(5, expressions);

    assertEquals(2, matchCallableReferenceExpression(
      builder().withClassFqn("java.lang.String").build(),
      expressions
    ));
    assertEquals(1, matchCallableReferenceExpression(
      builder().withClassFqn("java.util.Objects").build(),
      expressions
    ));
    assertEquals(1, matchCallableReferenceExpression(
      builder().withClassFqn("java.util.function.Function").build(),
      expressions
    ));
    assertEquals(1, matchCallableReferenceExpression(
      builder().withClassFqn("MethodReferences").build(),
      expressions
    ));

    assertEquals(1, matchCallableReferenceExpression(
      builder().withClassFqn("java.lang.String").withMethodName("toUpperCase").build(),
      expressions
    ));
    assertEquals(1, matchCallableReferenceExpression(
      builder().withClassFqn("java.util.Objects").withMethodName("hashCode").build(),
      expressions
    ));
    assertEquals(1, matchCallableReferenceExpression(
      builder().withClassFqn("java.util.function.Function").withMethodName("apply").build(),
      expressions
    ));
    assertEquals(1, matchCallableReferenceExpression(
      builder().withClassFqn("MethodReferences").withMethodName("bar").build(),
      expressions
    ));

    assertEquals(3, matchCallableReferenceExpression(
      builder().withArgumentsCount(1).build(),
      expressions
    ));

    //TODO uncomment after fix resolving:  generics (Java and Kotlin)  and  String (Kotlin, https://youtrack.jetbrains.com/issue/KT-25024)
    // one is for Function<String, String>#apply
    //assertEquals(3, matchCallableReferenceExpression(
    //  builder().withReturnType("java.lang.String").build(),
    //  expressions
    //));
  }
}
