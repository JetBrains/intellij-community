
package com.intellij.codeInsight.guess;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class GuessExprTypeTest extends LightCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/guess/exprType";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void test1() throws Exception{
    configureByFile(BASE_PATH + "/Test1.java");
    PsiType[] result = guessExprTypes();
    assertEquals(CommonClassNames.JAVA_LANG_STRING, assertOneElement(result).getCanonicalText());
  }

  public void test2() throws Exception{
    configureByFile(BASE_PATH + "/Test2.java");
    PsiType[] result = guessExprTypes();
    assertNotNull(result);
    assertEquals(CommonClassNames.JAVA_LANG_STRING, assertOneElement(result).getCanonicalText());
  }

  private static PsiType[] guessExprTypes() {
    int offset1 = getEditor().getSelectionModel().getSelectionStart();
    int offset2 = getEditor().getSelectionModel().getSelectionEnd();
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(getFile(), offset1, offset2);
    assertNotNull(expr);
    return GuessManager.getInstance(getProject()).guessTypeToCast(expr);
  }
}
