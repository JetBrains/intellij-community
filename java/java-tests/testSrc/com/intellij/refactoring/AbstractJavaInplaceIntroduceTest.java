/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.util.Pass;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.testFramework.IdeaTestCase;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightPlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * User: anna
 */
public abstract class AbstractJavaInplaceIntroduceTest extends AbstractInplaceIntroduceTest {

  @Nullable
  protected PsiExpression getExpressionFromEditor() {
    final PsiExpression expression = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    if (expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression) {
      return (PsiExpression)expression.getParent();
    }
    return expression;
  }

  protected static PsiLocalVariable getLocalVariableFromEditor() {
    final PsiLocalVariable localVariable = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()),
                                                                       PsiLocalVariable.class);
    assertNotNull(localVariable);
    return localVariable;
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk17();
  }

  @Override
  protected String getExtension() {
    return ".java";
  }

  protected abstract MyIntroduceHandler createIntroduceHandler();

  @Override
  protected AbstractInplaceIntroducer invokeRefactoring() {
    final MyIntroduceHandler introduceHandler = createIntroduceHandler();
    return invokeRefactoring(introduceHandler);
  }

  protected AbstractInplaceIntroducer invokeRefactoring(MyIntroduceHandler introduceHandler) {
    final PsiExpression expression = getExpressionFromEditor();
    if (expression != null) {
      introduceHandler.invokeImpl(LightPlatformTestCase.getProject(), expression, getEditor());
    } else {
      final PsiLocalVariable localVariable = getLocalVariableFromEditor();
      introduceHandler.invokeImpl(LightPlatformTestCase.getProject(), localVariable, getEditor());
    }
    return introduceHandler.getInplaceIntroducer();
  }
  
  protected void doTestInsideInjection(final Pass<AbstractInplaceIntroducer> pass) {
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(myTestRootDisposable);
    doTest(pass);
  }

  public interface MyIntroduceHandler {
    boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor);
    boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor);
    AbstractInplaceIntroducer getInplaceIntroducer();
  }
}
