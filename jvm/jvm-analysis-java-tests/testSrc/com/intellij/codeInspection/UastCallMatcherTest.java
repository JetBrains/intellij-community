package com.intellij.codeInspection;

import com.intellij.jvm.analysis.JvmAnalysisTestsUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.uast.UCallExpression;

import java.util.Locale;
import java.util.Set;

import static com.intellij.codeInspection.JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile;
import static com.intellij.codeInspection.UastCallMatcher.builder;

@TestDataPath("$CONTENT_ROOT/testData/codeInspection/uastCallMatcher")
public class UastCallMatcherTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/uastCallMatcher";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.setLanguageLevel(LanguageLevel.JDK_1_8);
    moduleBuilder.addJdk(IdeaTestUtil.getMockJdk18Path().getPath());
    moduleBuilder.addLibrary("javaUtil", PathUtil.getJarPathForClass(Locale.class));
  }

  public void testSimpleMatcher() {
    PsiFile file = myFixture.configureByFile("MyClass.java");
    Set<UCallExpression> expressions = getUElementsOfTypeFromFile(file, UCallExpression.class, ce -> ce.getMethodName() != null);
    assertSize(5, expressions);

    assertEquals(0, matchCallExpression(
      builder().withClassFqn("java.util.ArrayList").build(),
      expressions)
    );
    assertEquals(0, matchCallExpression(
      builder().withClassFqn("java.util.ArrayList").withMethodName("size").build(),
      expressions)
    );
    assertEquals(0, matchCallExpression(
      builder().withMethodName("size").build(),
      expressions)
    );
    assertEquals(0, matchCallExpression(
      builder().withClassFqn("java.util.ArrayList").withMethodName("addAll").withArgumentsCount(1).build(),
      expressions)
    );
    assertEquals(0, matchCallExpression(
      builder().withClassFqn("java.util.ArrayList").withMethodName("addAll").withArgumentTypes("java.util.Collection").build(),
      expressions)
    );

    assertEquals(4, matchCallExpression(
      builder().withClassFqn("java.lang.String").build(),
      expressions
    ));
    assertEquals(2, matchCallExpression(
      builder().withMethodName("toUpperCase").build(),
      expressions
    ));
    assertEquals(2, matchCallExpression(
      builder().withClassFqn("java.lang.String").withMethodName("toUpperCase").build(),
      expressions
    ));

    assertEquals(3, matchCallExpression(
      builder().withReturnType("java.lang.String").build(),
      expressions
    ));
    assertEquals(2, matchCallExpression(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").build(),
      expressions
    ));
    assertEquals(1, matchCallExpression(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").withArgumentsCount(1).build(),
      expressions
    ));
    assertEquals(1, matchCallExpression(
      builder().withReturnType("java.lang.String").withMethodName("toUpperCase").withArgumentTypes("java.util.Locale").build(),
      expressions
    ));

    assertEquals(2, matchCallExpression(
      builder().withArgumentsCount(0).build(),
      expressions
    ));
    assertEquals(3, matchCallExpression(
      builder().withArgumentsCount(1).build(),
      expressions
    ));

    assertEquals(1, matchCallExpression(
      builder().withArgumentTypes("java.util.Locale").build(),
      expressions
    ));
    assertEquals(1, matchCallExpression(
      builder().withArgumentTypes("java.util.Collection").withMatchArgumentTypeInheritors(true).build(),
      expressions
    ));
  }

  private static int matchCallExpression(UastCallMatcher matcher, Set<UCallExpression> expressions) {
    return (int)expressions.stream().filter(matcher::testCallExpression).count();
  }
}
