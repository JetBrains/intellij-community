/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.generation.ClassMember;
import com.intellij.codeInsight.generation.RecordConstructorMember;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.intention.LowPriorityAction;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInsight.intention.impl.ParameterClassMember;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateBuilderImpl;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.MemberChooser;
import com.intellij.java.JavaBundle;
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
import com.intellij.psi.util.JavaElementKind;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class DefineParamsDefaultValueAction extends PsiElementBaseIntentionAction implements Iconable, LowPriorityAction {
  private static final Logger LOG = Logger.getInstance(DefineParamsDefaultValueAction.class);

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("generate.overloaded.method.with.default.parameter.values");
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
    if (parameterList.isEmpty()) {
      return false;
    }
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null || (containingClass.isInterface() && !PsiUtil.isLanguageLevel8OrHigher(method))) {
      return false;
    }
    setText(QuickFixBundle.message("generate.overloaded.method.or.constructor.with.default.parameter.values",
                                   JavaElementKind.fromElement(method).lessDescriptive().object()));
    return true;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiMethod method = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
    assert method != null;
    PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] parameters = getParams(element, parameterList);
    if (parameters == null || parameters.length == 0) return;
    final PsiMethod methodPrototype = generateMethodPrototype(method, parameters);
    final PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return;
    final PsiMethod existingMethod = containingClass.findMethodBySignature(methodPrototype, false);
    if (existingMethod != null) {
      if (containingClass.isPhysical()) {
        editor.getCaretModel().moveToOffset(existingMethod.getTextOffset());
        HintManager.getInstance().showErrorHint(editor,
                                                JavaBundle.message("default.param.value.warning", existingMethod.isConstructor() ? 0 : 1));
      }
      return;
    }

    if (element.isPhysical() && !FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    Runnable runnable = () -> {
      final PsiMethod prototype = (PsiMethod)containingClass.addBefore(methodPrototype, method);
      CommonJavaRefactoringUtil.fixJavadocsForParams(prototype, ContainerUtil.set(prototype.getParameterList().getParameters()));


      PsiCodeBlock body = prototype.getBody();
      final String callArgs =
        "(" + StringUtil.join(parameterList.getParameters(), psiParameter -> {
          if (ArrayUtil.find(parameters, psiParameter) > -1) return TypeUtils.getDefaultValue(psiParameter.getType());
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
      final PsiExpression expr;
      if (stmt instanceof PsiReturnStatement) {
        expr = ((PsiReturnStatement)stmt).getReturnValue();
      } else if (stmt instanceof PsiExpressionStatement) {
        expr = ((PsiExpressionStatement)stmt).getExpression();
      }
      else {
        expr = null;
      }
      if (expr instanceof PsiMethodCallExpression) {
        PsiExpression[] args = ((PsiMethodCallExpression)expr).getArgumentList().getExpressions();
        PsiExpression[] toDefaults = ContainerUtil.map2Array(parameters, PsiExpression.class, (parameter -> args[parameterList.getParameterIndex(parameter)]));
        startTemplate(project, editor, toDefaults, prototype);
      }
    };
    if (startInWriteAction() || !containingClass.isPhysical()) {
      runnable.run();
    } else {
      ApplicationManager.getApplication().runWriteAction(runnable);
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project,
                                                       @NotNull Editor editor,
                                                       @NotNull PsiFile file) {
    invoke(project, editor, file);
    return IntentionPreviewInfo.DIFF;
  }

  public static void startTemplate(@NotNull Project project,
                                   Editor editor,
                                   PsiExpression[] argsToBeDelegated,
                                   PsiMethod delegateMethod) {
    TemplateBuilderImpl builder = new TemplateBuilderImpl(delegateMethod);
    RangeMarker rangeMarker = editor.getDocument().createRangeMarker(delegateMethod.getTextRange());
    for (final PsiExpression exprToBeDefault  : argsToBeDelegated) {
      builder.replaceElement(exprToBeDefault, new TextExpression(exprToBeDefault.getText()));
    }
    Template template = builder.buildTemplate();
    editor.getCaretModel().moveToOffset(rangeMarker.getStartOffset());

    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    editor.getDocument().deleteString(rangeMarker.getStartOffset(), rangeMarker.getEndOffset());

    rangeMarker.dispose();

    CreateFromUsageBaseFix.startTemplate(editor, template, project);
  }

  private static PsiParameter @Nullable [] getParams(@NotNull PsiElement element, @NotNull PsiParameterList parameterList) {
    final PsiParameter[] parameters = parameterList.getParameters();
    if (parameters.length == 1 || !parameterList.isPhysical()) {
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
      new MemberChooser<>(members, false, true, parameterList.getProject());
    if (idx >= 0) {
      chooser.selectElements(new ClassMember[] {members[idx]});
    }
    else {
      chooser.selectElements(members);
    }
    chooser.setTitle(QuickFixBundle.message("choose.default.value.parameters.popup.title"));
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
