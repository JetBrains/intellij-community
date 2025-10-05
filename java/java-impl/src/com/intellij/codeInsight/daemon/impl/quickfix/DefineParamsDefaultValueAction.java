// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.RecordConstructorMember;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.impl.ParameterClassMember;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInspection.redundantCast.RemoveRedundantCastUtil;
import com.intellij.icons.AllIcons;
import com.intellij.java.JavaBundle;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.lang.jvm.JvmModifier;
import com.intellij.modcommand.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.*;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.ui.NewUiValue;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class DefineParamsDefaultValueAction extends PsiBasedModCommandAction<PsiElement> implements DumbAware {
  private static final Logger LOG = Logger.getInstance(DefineParamsDefaultValueAction.class);

  public DefineParamsDefaultValueAction() {
    super(PsiElement.class);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("generate.overloaded.method.with.default.parameter.values");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!JavaLanguage.INSTANCE.equals(element.getLanguage())) return null;
    final PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class, PsiCodeBlock.class);
    String message;
    if (parent instanceof PsiMethod method) {
      if (!method.hasModifier(JvmModifier.ABSTRACT) && method.getBody() == null) return null;
      final PsiParameterList parameterList = method.getParameterList();
      if (parameterList.isEmpty()) return null;
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null || (containingClass.isInterface() && !PsiUtil.isAvailable(JavaFeature.EXTENSION_METHODS, method))) {
        return null;
      }
      if ((containingClass instanceof PsiImplicitClass || containingClass instanceof PsiAnonymousClass) && method.isConstructor()) {
        return null; // constructors can't be declared here, code is broken so don't suggest generating more broken code
      }
      if (containingClass.isAnnotationType()) {
        // Method with parameters in annotation is a compilation error; there's no sense to create overload
        return null;
      }
      message = QuickFixBundle.message("generate.overloaded.method.or.constructor.with.default.parameter.values",
                                       JavaElementKind.fromElement(method).lessDescriptive().object());
    }
    else if (parent instanceof PsiClass aClass && aClass.isRecord()) {
      PsiRecordHeader header = aClass.getRecordHeader();
      if (header == null || header.getTextOffset() + header.getTextLength() < element.getTextOffset()) return null;
      if (header.getRecordComponents().length == 0) return null;
      message = QuickFixBundle.message("generate.overloaded.method.or.constructor.with.default.parameter.values",
                                       JavaElementKind.CONSTRUCTOR.lessDescriptive().object());
    }
    else {
      return null;
    }
    return Presentation.of(message)
      .withIcon(NewUiValue.isEnabled() ? null : AllIcons.Actions.RefactoringBulb)
      .withPriority(PriorityAction.Priority.LOW);
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiElement element) {
    PsiElement parent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
    PsiMethod method;
    if (parent instanceof PsiMethod m) {
      method = m;
    }
    else if (parent instanceof PsiClass aClass && aClass.isRecord()) {
      method = JavaPsiRecordUtil.findCanonicalConstructor(aClass);
      assert method != null;
    }
    else throw new AssertionError();
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length == 1) {
      return ModCommand.psiUpdate(method, (m, updater) -> {
        PsiMethod writableMethod = updater.getWritable(m);
        invoke(context.project(), writableMethod, updater, writableMethod.getParameterList().getParameters());
      });
    }
    List<ParameterClassMember> members = ContainerUtil.map(parameters, ParameterClassMember::new);
    int idx = getSelectedIndex(element);
    List<ParameterClassMember> defaultSelection = idx >= 0 ? List.of(members.get(idx)) : members;
    return ModCommand.chooseMultipleMembers(
      QuickFixBundle.message("choose.default.value.parameters.popup.title"),
      members, defaultSelection, 
      sel -> ModCommand.psiUpdate(context, updater -> {
        invoke(context.project(), updater.getWritable(method), updater,
               ContainerUtil.map2Array(sel, PsiParameter.EMPTY_ARRAY,
                                       s -> updater.getWritable(((ParameterClassMember)s).getParameter())));
      }));
  }

  private static int getSelectedIndex(@NotNull PsiElement element) {
    PsiVariable selected = PsiTreeUtil.getParentOfType(element, PsiParameter.class, PsiRecordComponent.class);
    if (selected instanceof PsiParameter parameter) {
      PsiParameterList parameterList = (PsiParameterList)parameter.getParent();
      return parameterList.getParameterIndex(parameter);
    }
    else if (selected instanceof PsiRecordComponent recordComponent) {
      PsiRecordHeader recordHeader = (PsiRecordHeader)recordComponent.getParent();
      return ArrayUtil.find(recordHeader.getRecordComponents(), recordComponent);
    }
    else {
      return -1;
    }
  }

  private static void invoke(@NotNull Project project, @NotNull PsiMethod method, 
                             @NotNull ModPsiUpdater updater, @NotNull PsiParameter @NotNull [] parameters) {
    PsiParameterList parameterList = method.getParameterList();
    if (parameters.length == 0) return;
    final PsiMethod methodPrototype = generateMethodPrototype(method, parameters);
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return;
    for (PsiMethod existingMethod : containingClass.findMethodsByName(method.getName(), false)) {
      if (MethodSignatureUtil.areParametersErasureEqual(existingMethod, methodPrototype)) {
        updater.moveCaretTo(existingMethod.getTextOffset());
        String description = RefactoringUIUtil.getDescription(existingMethod, false);
        updater.message(StringUtil.capitalize(JavaBundle.message("default.param.value.warning", description)));
        return;
      }
    }

    final PsiMethod prototype = (PsiMethod)containingClass.addBefore(methodPrototype, method);
    CommonJavaRefactoringUtil.fixJavadocsForParams(prototype, Set.of(prototype.getParameterList().getParameters()));

    PsiCodeBlock body = prototype.getBody();
    final String callArgs =
      "(" + StringUtil.join(parameterList.getParameters(), psiParameter -> {
        if (ArrayUtil.find(parameters, psiParameter) > -1) {
          PsiType type = GenericsUtil.getVariableTypeByExpressionType(psiParameter.getType());
          String defaultValue = TypeUtils.getDefaultValue(type);
          return defaultValue.equals(JavaKeywords.NULL) ? "(" + type.getCanonicalText() + ")null" : defaultValue;
        }
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
      for (PsiExpression exprToBeDefault : toDefaults) {
        if (exprToBeDefault instanceof PsiTypeCastExpression cast && RedundantCastUtil.isCastRedundant(cast)) {
          exprToBeDefault = RemoveRedundantCastUtil.removeCast(cast);
        }
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
    final PsiModifierList modifierList = prototype.getModifierList();
    modifierList.setModifierProperty(PsiModifier.ABSTRACT, false);
    modifierList.setModifierProperty(PsiModifier.NATIVE, false);
    if (body != null) {
      body.replace(emptyBody);
    } else {
      prototype.add(emptyBody);
    }

    final PsiClass aClass = method.getContainingClass();
    if (aClass != null && aClass.isInterface() && !method.hasModifierProperty(PsiModifier.STATIC)) {
      modifierList.setModifierProperty(PsiModifier.DEFAULT, true);
    }

    for (PsiAnnotation annotation : modifierList.getAnnotations()) {
      if (annotation.hasQualifiedName(CommonClassNames.JAVA_LANG_OVERRIDE)) {
        annotation.delete();
      }
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
