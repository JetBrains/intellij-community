/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

import java.util.Collection;

public class Java8ExpressionsCheckTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/expressions";

  public void testSecondConflictResolutionOnSameMethodCall() throws Exception {
    doTestAllMethodCallExpressions();
  }

  public void testNestedLambdaAdditionalConstraints() throws Exception {
    doTestAllMethodCallExpressions();
  }

  public void testAvoidClassRefCachingDuringInference() throws Exception {
    doTestAllMethodCallExpressions();
  }

  public void testInfinitiveParameterBoundsCheck() throws Exception {
    doTestAllMethodCallExpressions();
  }

  private void doTestAllMethodCallExpressions() {
    configureByFile(BASE_PATH + "/" + getTestName(false) + ".java");
    final Collection<PsiCallExpression> methodCallExpressions = PsiTreeUtil.findChildrenOfType(getFile(), PsiCallExpression.class);
    for (PsiCallExpression expression : methodCallExpressions) {
      getPsiManager().dropResolveCaches();
      assertNotNull("Failed inference for: " + expression.getText(), expression.getType());
    }

    final Collection<PsiReferenceParameterList> parameterLists = PsiTreeUtil.findChildrenOfType(getFile(), PsiReferenceParameterList.class);
    for (PsiReferenceParameterList list : parameterLists) {
      getPsiManager().dropResolveCaches();
      final PsiType[] arguments = list.getTypeArguments();
      assertNotNull("Failed inference for: " + list.getParent().getText(), arguments);
    }
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
}
