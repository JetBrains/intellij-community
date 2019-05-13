package com.intellij.codeInspection.tests.kotlin;

import com.intellij.jvm.analysis.JvmAnalysisKtTestsUtil;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import com.intellij.util.PathUtil;
import kotlin.KotlinVersion;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.uast.ULiteralExpression;
import org.junit.Assume;

import java.util.Set;

import static com.intellij.codeInspection.NonNlsUastUtil.isNonNlsStringLiteral;
import static com.intellij.codeInspection.tests.JvmAnalysisTestsUastUtil.getUElementsOfTypeFromFile;

@TestDataPath("$CONTENT_ROOT/testData/codeInspection/nonNls")
public class KtNonNlsUastUtilTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Assume.assumeTrue(KotlinVersion.CURRENT.isAtLeast(1, 2, 60));
  }

  @Override
  protected String getBasePath() {
    return JvmAnalysisKtTestsUtil.TEST_DATA_PROJECT_RELATIVE_BASE_PATH + "/codeInspection/nonNls";
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    moduleBuilder.addLibrary("annotations", PathUtil.getJarPathForClass(NonNls.class));
  }

  public void testNonNlsStringLiterals() {
    PsiFile file = myFixture.configureByFile("NonNlsStringLiteral.kt");
    Set<ULiteralExpression> expressions = getUElementsOfTypeFromFile(file, ULiteralExpression.class);
    assertSize(20, expressions); // multiline string literal is processed as 4 string literals
    expressions.forEach(expression -> assertTrue(isNonNlsStringLiteral(expression)));
  }

  public void testPlainStringLiterals() {
    PsiFile file = myFixture.configureByFile("PlainStringLiteral.kt");
    Set<ULiteralExpression> expressions = getUElementsOfTypeFromFile(file, ULiteralExpression.class);
    assertSize(9, expressions);
    expressions.forEach(expression -> assertFalse(isNonNlsStringLiteral(expression)));
  }

  public void testLiteralsInNonNlsClass() {
    PsiFile file = myFixture.configureByFile("LiteralsInNonNlsClass.kt");
    Set<ULiteralExpression> expressions = getUElementsOfTypeFromFile(file, ULiteralExpression.class);
    assertSize(8, expressions);
    expressions.forEach(expression -> assertTrue(isNonNlsStringLiteral(expression)));
  }
}

