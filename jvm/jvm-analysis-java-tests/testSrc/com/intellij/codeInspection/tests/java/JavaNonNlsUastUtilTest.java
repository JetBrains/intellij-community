package com.intellij.codeInspection.tests.java;

import com.intellij.jvm.analysis.JavaJvmAnalysisTestUtil;
import com.intellij.openapi.application.PathManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.uast.ULiteralExpression;

import java.io.File;
import java.util.Set;

import static com.intellij.codeInspection.NonNlsUastUtil.isNonNlsStringLiteral;
import static com.intellij.jvm.analysis.internal.testFramework.JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile;

@TestDataPath("$CONTENT_ROOT/testData/codeInspection/nonNls")
public class JavaNonNlsUastUtilTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaJvmAnalysisTestUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/nonNls";
  }

  @Override
  protected String getTestDataPath() {
    return PathManager.getCommunityHomePath().replace(File.separatorChar, '/') + getBasePath();
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
