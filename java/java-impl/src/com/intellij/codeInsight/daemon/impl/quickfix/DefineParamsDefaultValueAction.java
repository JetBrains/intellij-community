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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.impl.ParameterClassMember;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.MemberChooser;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * User: anna
 * Date: 8/2/12
 */
public class DefineParamsDefaultValueAction extends PsiElementBaseIntentionAction implements Iconable, LowPriorityAction {
  private static final Logger LOG = Logger.getInstance(DefineParamsDefaultValueAction.class);

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return "Generate overloaded method with default parameter values";
  }

  @Override
  public Icon getIcon(int flags) {
    return AllIcons.Actions.RefactoringBulb;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) {
      return false;
    }
    final PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiCodeBlock.class);
    if (!(parent instanceof PsiMethod)) {
      return false;
    }
    final PsiMethod method = (PsiMethod)parent;
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.getParametersCount() == 0) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || (containingClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(method))) {
      return false;
    }
    setText("Generate overloaded " + (method.isConstructor() ? "constructor" : "method") + " with default parameter values");
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiParameter[] parameters = getParams(element);
    if (parameters == null || parameters.length == 0) return;
    final PsiMethod method = (PsiMethod)parameters[0].getDeclarationScope();
    final PsiMethod methodPrototype = generateMethodPrototype(method, parameters);
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return;
    final PsiMethod existingMethod = containingClass.findMethodBySignature(methodPrototype, false);
    if (existingMethod != null) {
      editor.getCaretModel().moveToOffset(existingMethod.getTextOffset());
      HintManager.getInstance().showErrorHint(editor, (existingMethod.isConstructor() ? "Constructor" : "Method") +
                                                      " with the chosen signature already exists");
      return;
    }

    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    Runnable runnable = () -> {
      final PsiMethod prototype = (PsiMethod)containingClass.addBefore(methodPrototype, method);
      RefactoringUtil.fixJavadocsForParams(prototype, new HashSet<>(Arrays.asList(prototype.getParameterList().getParameters())));
      TemplateBuilderImpl builder = new TemplateBuilderImpl(prototype);

      PsiCodeBlock body = prototype.getBody();
      final String callArgs =
        "(" + StringUtil.join(method.getParameterList().getParameters(), psiParameter -> {
          if (ArrayUtil.find(parameters, psiParameter) > -1) return "IntelliJIDEARulezzz";
          return psiParameter.getName();
        }, ",") + ");";
      final String methodCall;
      if (method.getReturnType() == null) {
        methodCall = "this";
      } else if (!PsiType.VOID.equals(method.getReturnType())) {
        methodCall = "return " + method.getName();
      } else {
        methodCall = method.getName();
      }
      LOG.assertTrue(body != null);
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
    };
    if (startInWriteAction()) {
      runnable.run();
    } else {
      ApplicationManager.getApplication().runWriteAction(runnable);
    }
  }

  @Nullable
  protected PsiParameter[] getParams(PsiElement element) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    assert method != null;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 1) {
      return parameters;
    }
    final ParameterClassMember[] members = new ParameterClassMember[parameters.length];
    for (int i = 0; i < members.length; i++) {
      members[i] = new ParameterClassMember(parameters[i]);
    }
    final PsiParameter selectedParam = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    final int idx = selectedParam != null ? ArrayUtil.find(parameters, selectedParam) : -1;
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return idx >= 0 ? new PsiParameter[] {selectedParam} : null;
    }
    final MemberChooser<ParameterClassMember> chooser =
      new MemberChooser<>(members, false, true, element.getProject());
    if (idx >= 0) {
      chooser.selectElements(new ClassMember[] {members[idx]});
    }
    else {
      chooser.selectElements(members);
    }
    chooser.setTitle("Choose Default Value Parameters");
    chooser.setCopyJavadocVisible(false);
    if (chooser.showAndGet()) {
      final List<ParameterClassMember> elements = chooser.getSelectedElements();
      if (elements != null) {
        PsiParameter[] params = new PsiParameter[elements.size()];
        for (int i = 0; i < params.length; i++) {
          params[i] = elements.get(i).getParameter();
        }
        return params;
      }
    }
    return null;
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

    final PsiClass aClass = method.getContainingClass();
    if (aClass != null && aClass.isInterface() && !method.hasModifierProperty(PsiModifier.STATIC)) {
      prototype.getModifierList().setModifierProperty(PsiModifier.DEFAULT, true);
    }

    final PsiParameterList parameterList = method.getParameterList();
    Arrays.sort(params, (p1, p2) -> {
      final int parameterIndex1 = parameterList.getParameterIndex(p1);
      final int parameterIndex2 = parameterList.getParameterIndex(p2);
      return parameterIndex1 > parameterIndex2 ? -1 : 1;
    });

    for (PsiParameter param : params) {
      final int parameterIndex = parameterList.getParameterIndex(param);
      prototype.getParameterList().getParameters()[parameterIndex].delete();
    }
    return prototype;
  }
}
