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

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.icons.AllIcons;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;

/**
 * User: anna
 */
public class DelegateWithDefaultParamValueIntentionAction extends PsiElementBaseIntentionAction implements Iconable, LowPriorityAction {
  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiParameter parameter = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    if (parameter != null) {
      if (!parameter.getLanguage().isKindOf(StdLanguages.JAVA)) return false;
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
    return AllIcons.Actions.RefactoringBulb;
  }

  private static PsiMethod generateMethodPrototype(PsiMethod method, PsiParameter... params) {
    final PsiMethod prototype = (PsiMethod)method.copy();
    final PsiCodeBlock body = prototype.getBody();
    final PsiCodeBlock emptyBody = JavaPsiFacade.getElementFactory(method.getProject()).createMethodFromText("void foo(){}", prototype).getBody();
    assert emptyBody != null;
    if (body != null) {
      body.replace(emptyBody);
    } else {
      prototype.getModifierList().setModifierProperty(PsiModifier.ABSTRACT, false);
      prototype.addBefore(emptyBody, null);
    }
    for (int i = params.length - 1; i >= 0; i--) {
      PsiParameter param = params[i];
      final int parameterIndex = method.getParameterList().getParameterIndex(param);
      prototype.getParameterList().getParameters()[parameterIndex].delete();
    }
    return prototype;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiParameter[] parameters = getParams(element);
    if (parameters == null || parameters.length == 0) return;
    final PsiMethod method = (PsiMethod)parameters[0].getDeclarationScope();
    final PsiMethod methodPrototype = generateMethodPrototype(method, parameters);
    final PsiMethod existingMethod = method.getContainingClass().findMethodBySignature(methodPrototype, false);
    if (existingMethod != null) {
      editor.getCaretModel().moveToOffset(existingMethod.getTextOffset());
      HintManager.getInstance().showErrorHint(editor, "Method with the chosen signature already exist");
      return;
    }

    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;
    
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        
        final PsiMethod prototype = (PsiMethod)method.getContainingClass().addBefore(methodPrototype, method);
        RefactoringUtil.fixJavadocsForParams(prototype, new HashSet<PsiParameter>(Arrays.asList(prototype.getParameterList().getParameters())));
        TemplateBuilderImpl builder = new TemplateBuilderImpl(prototype);

        PsiCodeBlock body = prototype.getBody();
        final String callArgs =
          "(" + StringUtil.join(method.getParameterList().getParameters(), new Function<PsiParameter, String>() {
            @Override
            public String fun(PsiParameter psiParameter) {
              if (ArrayUtil.find(parameters, psiParameter) > -1) return "IntelliJIDEARulezzz";
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
          for (PsiParameter parameter : parameters) {
            final PsiExpression exprToBeDefault =
              methodCallExp.getArgumentList().getExpressions()[method.getParameterList().getParameterIndex(parameter)];
            builder.replaceElement(exprToBeDefault, new TextExpression(""));
          }
          Template template = builder.buildTemplate();
          editor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());
    
          PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
          editor.getDocument().deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());
    
          rangeMarker.dispose();
    
          CreateFromUsageBaseFix.startTemplate(editor, template, project);
        }
      }
    };
    if (startInWriteAction()) {
      runnable.run();
    } else {
      ApplicationManager.getApplication().runWriteAction(runnable);
    }
  }

  @Nullable
  protected PsiParameter[] getParams(PsiElement element) {
    return new PsiParameter[]{PsiTreeUtil.getParentOfType(element, PsiParameter.class)};
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
