// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.impl.source.tree.injected.MyTestInjector;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.AbstractInplaceIntroduceTest;
import com.intellij.refactoring.introduce.inplace.AbstractInplaceIntroducer;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public abstract class AbstractJavaInplaceIntroduceTest extends AbstractInplaceIntroduceTest {
  @Nullable
  protected PsiExpression getExpressionFromEditor() {
    final PsiExpression expression = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()), PsiExpression.class);
    if (expression instanceof PsiReferenceExpression && expression.getParent() instanceof PsiMethodCallExpression) {
      return (PsiExpression)expression.getParent();
    }
    return expression;
  }

  protected PsiLocalVariable getLocalVariableFromEditor() {
    final PsiLocalVariable localVariable = PsiTreeUtil.getParentOfType(getFile().findElementAt(getEditor().getCaretModel().getOffset()),
                                                                       PsiLocalVariable.class);
    assertNotNull(localVariable);
    return localVariable;
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
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
      introduceHandler.invokeImpl(getProject(), expression, getEditor());
    } else {
      final PsiLocalVariable localVariable = getLocalVariableFromEditor();
      introduceHandler.invokeImpl(getProject(), localVariable, getEditor());
    }
    return introduceHandler.getInplaceIntroducer();
  }
  
  protected void doTestInsideInjection(Consumer<? super AbstractInplaceIntroducer> pass) {
    MyTestInjector testInjector = new MyTestInjector(getPsiManager());
    testInjector.injectAll(getTestRootDisposable());
    doTest(pass);
  }

  @SuppressWarnings("UnusedReturnValue")
  public interface MyIntroduceHandler {
    boolean invokeImpl(Project project, @NotNull PsiExpression selectedExpr, Editor editor);
    boolean invokeImpl(Project project, PsiLocalVariable localVariable, Editor editor);
    AbstractInplaceIntroducer getInplaceIntroducer();
  }
}