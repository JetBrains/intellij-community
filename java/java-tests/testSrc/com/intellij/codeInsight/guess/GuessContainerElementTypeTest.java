
package com.intellij.codeInsight.guess;

import com.intellij.JavaTestUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class GuessContainerElementTypeTest extends LightCodeInsightTestCase {

  private static final String BASE_PATH = "/codeInsight/guess/containerElement";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void test1() throws Exception{
    configureByFile(BASE_PATH + "/Test1.java");
    PsiType[] result = guessContainerElementTypes();
    assertNotNull(result);
    assertEquals(result.length, 1);
    assertTrue(result[0].equalsToText("java.lang.String"));
  }

  public void test2() throws Exception{
    configureByFile(BASE_PATH + "/Test2.java");
    PsiType[] result = guessContainerElementTypes();
    assertNotNull(result);
    assertEquals(result.length, 1);
    assertTrue(result[0].equalsToText("java.lang.String"));
  }

  private PsiType[] guessContainerElementTypes() {
    int offset = getEditor().getCaretModel().getOffset();
    PsiElement element = getFile().findElementAt(offset);
    assertNotNull(element);
    PsiElement parent = element.getParent();
    assertTrue(parent instanceof PsiReferenceExpression);
    PsiExpression expr = (PsiExpression)parent;
    return GuessManager.getInstance(getProject()).guessContainerElementType(expr, null);
  }
}