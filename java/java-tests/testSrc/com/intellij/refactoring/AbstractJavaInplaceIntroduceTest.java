/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.impl.JavaSdkImpl;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 */
public abstract class AbstractJavaInplaceIntroduceTest extends AbstractInplaceIntroduceTest {

  @Override
  protected void setUp() throws Exception {
    IdeaTestCase.initPlatformPrefix();
    super.setUp();
  }

  @Override
  @Nullable
  protected PsiExpression getExpressionFromEditor() {
    final PsiExpression expression =
      PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    if (expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression) {
      return (PsiExpression)expression.getParent();
    }
    return expression;
  }


  @Override
  protected PsiLocalVariable getLocalVariableFromEditor() {
    final PsiLocalVariable localVariable =
    PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiLocalVariable.class);
    assertNotNull(localVariable);
    return localVariable;
  }

  @Override
  protected Sdk getProjectJDK() {
    return JavaSdkImpl.getMockJdk17();
  }
}
