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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * User: anna
 */
public class DelegateWithDefaultParamValueIntentionAction extends PsiElementBaseIntentionAction implements Iconable {
   public static final Icon REFACTORING_BULB = IconLoader.getIcon("/actions/refactoringBulb.png");
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    if (parameter != null) {
      final PsiElement declarationScope = parameter.getDeclarationScope();
      if (declarationScope instanceof PsiMethod) {
        final PsiMethod method = (PsiMethod)declarationScope;
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass != null && !containingClass.isInterface()) {
          return containingClass.findMethodBySignature(generateMethodPrototype(method, parameter), false) == null;
        }
      }
    }
    return false;
  }

  @Override
  public Icon getIcon(int flags) {
    return REFACTORING_BULB;
  }

  private static PsiMethod generateMethodPrototype(PsiMethod method, PsiParameter param) {
    final PsiMethod prototype = (PsiMethod)method.copy();
    final PsiCodeBlock body = prototype.getBody();
    if (body != null) {
      for (PsiStatement psiStatement : body.getStatements()) {
        psiStatement.delete();
      }
    }
    final int parameterIndex = method.getParameterList().getParameterIndex(param);
    prototype.getParameterList().getParameters()[parameterIndex].delete();
    return prototype;
  }

  @Override
  public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
    final PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    final PsiMethod method = (PsiMethod)parameter.getDeclarationScope();
    final PsiMethod prototype = (PsiMethod)method.getContainingClass().addBefore(generateMethodPrototype(method, parameter), method);

    TemplateBuilderImpl builder = new TemplateBuilderImpl(prototype);

    PsiCodeBlock body = prototype.getBody();
    final String callArgs =
      "(" + StringUtil.join(method.getParameterList().getParameters(), new Function<PsiParameter, String>() {
        @Override
        public String fun(PsiParameter psiParameter) {
          if (psiParameter.equals(parameter)) return "IntelliJIDEARulezzz";
          return psiParameter.getName();
        }
      }, ",") + ");";
    final String methodCall;
    if (method.getReturnType() == null) {
      methodCall = "this";
    } else if (method.getReturnType() != PsiType.VOID) {
      methodCall = "return " + method.getName();
    } else {
      methodCall = method.getName();
    }
    body.add(JavaPsiFacade.getElementFactory(project).createStatementFromText(methodCall + callArgs, method));
    body = (PsiCodeBlock)CodeStyleManager.getInstance(project).reformat(body);
    final PsiStatement stmt = body.getStatements()[0];
    PsiExpression expr = null;
    if (stmt instanceof PsiReturnStatement) {
      expr = ((PsiReturnStatement)stmt).getReturnValue();
    } else if (stmt instanceof PsiExpressionStatement) {
      expr = ((PsiExpressionStatement)stmt).getExpression();
    }
    if (expr instanceof PsiMethodCallExpression) {
      PsiMethodCallExpression methodCallExp = (PsiMethodCallExpression)expr;
      RangeMarker rangeMarker = editor.getDocument().createRangeMarker(prototype.getTextRange());
      final PsiExpression exprToBeDefault =
        methodCallExp.getArgumentList().getExpressions()[method.getParameterList().getParameterIndex(parameter)];
      builder.replaceElement(exprToBeDefault, new TextExpression(""));
      Template template = builder.buildTemplate();
      editor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());

      PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
      editor.getDocument().deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());

      rangeMarker.dispose();

      CreateFromUsageBaseFix.startTemplate(editor, template, project);
    }
  }

  @NotNull
  @Override
  public String getText() {
    return "Generate delegated method with default parameter value";
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }
}
