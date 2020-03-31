
/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.codeInsight.guess;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

public class GuessExprTypeTest extends LightJavaCodeInsightTestCase {
  private static final String BASE_PATH = "/codeInsight/guess/exprType";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void test1() {
    configureByFile(BASE_PATH + "/Test1.java");
    PsiType[] result = guessExprTypes();
    assertEquals(CommonClassNames.JAVA_LANG_STRING, assertOneElement(result).getCanonicalText());
  }

  public void test2() {
    configureByFile(BASE_PATH + "/Test2.java");
    PsiType[] result = guessExprTypes();
    assertNotNull(result);
    assertEquals(CommonClassNames.JAVA_LANG_STRING, assertOneElement(result).getCanonicalText());
  }

  private PsiType[] guessExprTypes() {
    int offset1 = getEditor().getSelectionModel().getSelectionStart();
    int offset2 = getEditor().getSelectionModel().getSelectionEnd();
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(getFile(), offset1, offset2);
    assertNotNull(expr);
    return GuessManager.getInstance(getProject()).guessTypeToCast(expr);
  }
}
