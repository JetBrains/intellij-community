package com.intellij.codeInspection;

import com.intellij.jvm.analysis.JvmAnalysisTestsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.uast.ULiteralExpression;

import java.util.Set;

import static com.intellij.codeInspection.NonNlsUastUtil.isNonNlsStringLiteral;
import static com.intellij.codeInspection.JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile;

@TestDataPath("$CONTENT_ROOT/testData/codeInspection/nonNls")
public class NonNlsUastUtilTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JvmAnalysisTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/nonNls";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("annotations", PathUtil.getJarPathForClass(NonNls.class));
  }

  public void testNonNlsStringLiterals() {
    PsiFile file = myFixture.configureByFile("NonNlsStringLiteral.java");
    Set<ULiteralExpression> expressions = getUElementsOfTypeFromFile(file, ULiteralExpression.class);
    assertSize(12, expressions);
    expressions.forEach(expression -> assertTrue(isNonNlsStringLiteral(expression)));
  }

  public void testPlainStringLiterals() {
    PsiFile file = myFixture.configureByFile("PlainStringLiteral.java");
    Set<ULiteralExpression> expressions = getUElementsOfTypeFromFile(file, ULiteralExpression.class);
    assertSize(7, expressions);
    expressions.forEach(expression -> assertFalse(isNonNlsStringLiteral(expression)));
  }

  public void testLiteralsInNonNlsClass() {
    PsiFile file = myFixture.configureByFile("LiteralsInNonNlsClass.java");
    Set<ULiteralExpression> expressions = getUElementsOfTypeFromFile(file, ULiteralExpression.class);
    assertSize(7, expressions);
    expressions.forEach(expression -> assertTrue(isNonNlsStringLiteral(expression)));
  }
}
