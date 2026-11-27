
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

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class GuessExprTypeTest {
  private static final String BASE_PATH = "/codeInsight/guess/exprType";

  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(projectFixture);

  @Test
  public void test1() {
    daemonFixture.get().configureByFile(BASE_PATH + "/Test1.java");
    ReadAction.runBlocking(() -> {
      PsiType[] result = guessExprTypes();
      assertThat(result).hasSize(1);
      assertEquals(CommonClassNames.JAVA_LANG_STRING, result[0].getCanonicalText());
    });
  }

  @Test
  public void test2() {
    daemonFixture.get().configureByFile(BASE_PATH + "/Test2.java");
    ReadAction.runBlocking(() -> {
      PsiType[] result = guessExprTypes();
      assertNotNull(result);
      assertThat(result).hasSize(1);
      assertEquals(CommonClassNames.JAVA_LANG_STRING, result[0].getCanonicalText());
    });
  }

  private PsiType[] guessExprTypes() {
    LightJavaCodeInsightTestFixture myFixture = daemonFixture.get().getFixture();
    int offset1 = myFixture.getEditor().getSelectionModel().getSelectionStart();
    int offset2 = myFixture.getEditor().getSelectionModel().getSelectionEnd();
    PsiExpression expr = CodeInsightUtil.findExpressionInRange(myFixture.getFile(), offset1, offset2);
    assertNotNull(expr);
    return GuessManager.getInstance(myFixture.getProject()).guessTypeToCast(expr);
  }
}
