// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.RecordConstructorMember;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.ParameterClassMember;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.modcommand.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class DefineParamsDefaultValueAction extends PsiBasedModCommandAction<PsiElement> {
  private static final Logger LOG = Logger.getInstance(DefineParamsDefaultValueAction.class);

  public DefineParamsDefaultValueAction() {
    super(PsiElement.class);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("generate.overloaded.method.with.default.parameter.values");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) return null;
    final PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiCodeBlock.class);
    if (!(parent instanceof PsiMethod method)) return null;
    final PsiParameterList parameterList = method.getParameterList();
    if (parameterList.isEmpty()) return null;
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || (containingClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(method))) return null;
    if (containingClass.isAnnotationType()) {
      // Method with parameters in annotation is a compilation error; there's no sense to create overload
      return null;
    }
    return Presentation.of(QuickFixBundle.message("generate.overloaded.method.or.constructor.with.default.parameter.values",
                                                  JavaElementKind.fromElement(method).lessDescriptive().object()))
      .withIcon(AllIcons.Actions.RefactoringBulb)
      .withPriority(PriorityAction.Priority.LOW);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiElement element) {
    PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    assert method != null;
    PsiParameterList parameterList = method.getParameterList();
    PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length == 1) {
      return ModCommand.psiUpdate(method, (m, updater) -> invoke(context.project(), m, updater,
                                                                 updater.getWritable(m).getParameterList().getParameters()));
    }
    List<ParameterClassMember> members = ContainerUtil.map(parameters, ParameterClassMember::new);
    PsiParameter selectedParam = PsiTreeUtil.getParentOfType(element, PsiParameter.class);
    int idx = selectedParam != null ? ArrayUtil.find(parameters, selectedParam) : -1;
    List<ParameterClassMember> defaultSelection = idx >= 0 ? List.of(members.get(idx)) : members;
    return ModCommand.chooseMultipleMembers(
      QuickFixBundle.message("choose.default.value.parameters.popup.title"),
      members, defaultSelection, 
      sel -> ModCommand.psiUpdate(context, updater -> {
        invoke(context.project(), updater.getWritable(element), updater,
               ContainerUtil.map2Array(sel, PsiParameter.EMPTY_ARRAY,
                                       s -> updater.getWritable(((ParameterClassMember)s).getParameter())));
      }));
  }

  private static void invoke(@NotNull Project project, @NotNull PsiElement element, 
                             @NotNull ModPsiUpdater updater, @NotNull PsiParameter @NotNull [] parameters) {
    final PsiMethod method = PsiTreeUtil.getNonStrictParentOfType(element, PsiMethod.class);
    assert method != null;
    PsiParameterList parameterList = method.getParameterList();
    if (parameters.length == 0) return;
    final PsiMethod methodPrototype = generateMethodPrototype(method, parameters);
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return;
    final PsiMethod existingMethod = containingClass.findMethodBySignature(methodPrototype, false);
    if (existingMethod != null) {
      updater.moveTo(existingMethod.getTextOffset());
      updater.message(JavaBundle.message("default.param.value.warning",
                                         existingMethod.isConstructor() ? 0 : 1));
      return;
    }

    final PsiMethod prototype = (PsiMethod)containingClass.addBefore(methodPrototype, method);
    CommonJavaRefactoringUtil.fixJavadocsForParams(prototype, Set.of(prototype.getParameterList().getParameters()));

    PsiCodeBlock body = prototype.getBody();
    final String callArgs =
      "(" + StringUtil.join(parameterList.getParameters(), psiParameter -> {
        if (ArrayUtil.find(parameters, psiParameter) > -1) return TypeUtils.getDefaultValue(psiParameter.getType());
        return psiParameter.getName();
      }, ",") + ");";
    final String methodCall;
    if (method.getReturnType() == null) {
      methodCall = "this";
    }
    else if (!PsiTypes.voidType().equals(method.getReturnType())) {
      methodCall = "return " + method.getName();
    }
    else {
      methodCall = method.getName();
    }
    LOG.assertTrue(body != null);
    body.add(JavaPsiFacade.getElementFactory(project).createStatementFromText(methodCall + callArgs, method));
    body = (PsiCodeBlock)CodeStyleManager.getInstance(project).reformat(body);
    final PsiStatement stmt = body.getStatements()[0];
    final PsiExpression expr;
    if (stmt instanceof PsiReturnStatement returnStatement) {
      expr = returnStatement.getReturnValue();
    }
    else if (stmt instanceof PsiExpressionStatement exprStatement) {
      expr = exprStatement.getExpression();
    }
    else {
      expr = null;
    }
    if (expr instanceof PsiMethodCallExpression call) {
      PsiExpression[] args = call.getArgumentList().getExpressions();
      PsiExpression[] toDefaults =
        ContainerUtil.map2Array(parameters, PsiExpression.class, (parameter -> args[parameterList.getParameterIndex(parameter)]));
      ModTemplateBuilder builder = updater.templateBuilder();
      for (final PsiExpression exprToBeDefault : toDefaults) {
        builder.field(exprToBeDefault, new TextExpression(exprToBeDefault.getText()));
      }
    }
  }

  private static PsiMethod generateMethodPrototype(PsiMethod method, PsiParameter... params) {
    final PsiMethod prototype = JavaPsiRecordUtil.isCompactConstructor(method) ?
                                new RecordConstructorMember(method.getContainingClass(), false).generateRecordConstructor() :                            
                                (PsiMethod)method.copy();
    final PsiCodeBlock body = prototype.getBody();
    final PsiCodeBlock emptyBody = JavaPsiFacade.getElementFactory(method.getProject()).createCodeBlock();
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
    Arrays.sort(params, Comparator.comparingInt(parameterList::getParameterIndex).reversed());

    for (PsiParameter param : params) {
      final int parameterIndex = parameterList.getParameterIndex(param);
      Objects.requireNonNull(prototype.getParameterList().getParameter(parameterIndex)).delete();
    }
    return prototype;
  }
}
