package com.intellij.codeInspection;

import com.intellij.jvm.analysis.JvmAnalysisTestsUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UastContextKt;

import java.util.*;

import static com.intellij.codeInspection.UastCallMatcher.builder;

public class UastCallMatcherTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/uastCallMatcher";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("javaUtil", PathUtil.getJarPathForClass(Locale.class));
  }

  public void testSimpleMatcher() {
    PsiFile file = myFixture.configureByFile("MyClass.java");

    Set<UCallExpression> expressions = new HashSet<>();
    PsiTreeUtil.processElements(file, new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element) {
        UCallExpression callExpression = UastContextKt.toUElement(element, UCallExpression.class);
        if (callExpression != null && callExpression.getMethodName() != null) { // skip constructor calls
          expressions.add(callExpression);
        }
        return true;
      }
    });
    assertSize(5, expressions);

    assertEquals(0, match(
      builder().withReceiverType("java.util.ArrayList").build(),
      expressions)
    );
    assertEquals(0, match(
      builder().withReceiverType("java.util.ArrayList").withMethodName("size").build(),
      expressions)
    );
    assertEquals(0, match(
      builder().withMethodName("size").build(),
      expressions)
    );
    assertEquals(0, match(
      builder().withReceiverType("java.util.ArrayList").withMethodName("addAll").withArgumentsCount(1).build(),
      expressions)
    );
    assertEquals(0, match(
      builder().withReceiverType("java.util.ArrayList").withMethodName("addAll").withArgumentTypes("java.util.Collection").build(),
      expressions)
    );

    assertEquals(4, match(
      builder().withReceiverType("java.lang.String").build(),
      expressions
    ));
    assertEquals(2, match(
      builder().withMethodName("toUpperCase").build(),
      expressions
    ));
    assertEquals(2, match(
      builder().withReceiverType("java.lang.String").withMethodName("toUpperCase").build(),
      expressions
    ));

    assertEquals(3, match(
      builder().withReturnType("java.lang.String").build(),
      expressions
    ));
    assertEquals(2, match(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").build(),
      expressions
    ));
    assertEquals(1, match(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").withArgumentsCount(1).build(),
      expressions
    ));
    assertEquals(1, match(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").withArgumentTypes("java.util.Locale").build(),
      expressions
    ));

    assertEquals(2, match(
      builder().withArgumentsCount(0).build(),
      expressions
    ));
    assertEquals(3, match(
      builder().withArgumentsCount(1).build(),
      expressions
    ));

    assertEquals(1, match(
      builder().withArgumentTypes("java.util.Locale").build(),
      expressions
    ));
    assertEquals(1, match(
      builder().withArgumentTypes("java.util.Collection").withMatchArgumentTypeInheritors(true).build(),
      expressions
    ));
  }

  private static int match(UastCallMatcher matcher, Set<UCallExpression> expressions) {
    return (int)expressions.stream().filter(e -> matcher.testCallExpression(e)).count();
  }
}
