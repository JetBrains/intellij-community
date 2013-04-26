/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.generation.OverrideImplementUtil;
import com.intellij.codeInsight.generation.PsiMethodMember;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ChangeParameterClassFix extends ExtendsListFix {
  private ChangeParameterClassFix(PsiClass aClassToExtend, PsiClassType parameterClass) {
    super(aClassToExtend, parameterClass, true);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("change.parameter.class.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return
      super.isAvailable(project, file, startElement, endElement)
      && myClassToExtendFrom != null
      && myClassToExtendFrom.isValid()
      && myClassToExtendFrom.getQualifiedName() != null
      ;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable("is null when called from inspection") Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiClass myClass = (PsiClass)startElement;
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;
    ApplicationManager.getApplication().runWriteAction(
      new Runnable() {
        @Override
        public void run() {
          invokeImpl(myClass);
        }
      }
    );
    final Editor editor1 = CodeInsightUtil.positionCursor(project, myClass.getContainingFile(), myClass);
    if (editor1 == null) return;
    final Collection<CandidateInfo> toImplement = OverrideImplementUtil.getMethodsToOverrideImplement(myClass, true);
    if (!toImplement.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        ApplicationManager.getApplication().runWriteAction(
          new Runnable() {
            @Override
            public void run() {
              Collection<PsiMethodMember> members =
                ContainerUtil.map2List(toImplement, new Function<CandidateInfo, PsiMethodMember>() {
                  @Override
                  public PsiMethodMember fun(final CandidateInfo s) {
                    return new PsiMethodMember(s);
                  }
                });
              OverrideImplementUtil.overrideOrImplementMethodsInRightPlace(editor1, myClass, members, false);
            }
          });
      }
      else {
        //SCR 12599
        editor1.getCaretModel().moveToOffset(myClass.getTextRange().getStartOffset());

        OverrideImplementUtil.chooseAndImplementMethods(project, editor1, myClass);
      }
    }
    UndoUtil.markPsiFileForUndo(file);
  }

  public static void registerQuickFixAction(PsiType lType, PsiType rType, HighlightInfo info) {
    final PsiClass lClass = PsiUtil.resolveClassInClassTypeOnly(lType);
    final PsiClass rClass = PsiUtil.resolveClassInClassTypeOnly(rType);

    if (rClass == null || lClass == null) return;
    if (rClass instanceof PsiAnonymousClass) return;
    if (rClass.isInheritor(lClass, true)) return;
    if (lClass.isInheritor(rClass, true)) return;
    if (lClass == rClass) return;

    QuickFixAction.registerQuickFixAction(info, new ChangeParameterClassFix(rClass, (PsiClassType)lType));
  }

  public static void registerQuickFixActions(PsiCall methodCall, PsiExpressionList list, HighlightInfo highlightInfo) {
    final JavaResolveResult result = methodCall.resolveMethodGenerics();
    PsiMethod method = (PsiMethod)result.getElement();
    final PsiSubstitutor substitutor = result.getSubstitutor();
    PsiExpression[] expressions = list.getExpressions();
    if (method == null) return;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != expressions.length) return;
    for (int i = 0; i < expressions.length; i++) {
      final PsiExpression expression = expressions[i];
      final PsiParameter parameter = parameters[i];
      final PsiType expressionType = expression.getType();
      final PsiType parameterType = substitutor.substitute(parameter.getType());
      if (expressionType == null || expressionType instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(expressionType) || expressionType instanceof PsiArrayType) continue;
      if (parameterType instanceof PsiPrimitiveType || TypeConversionUtil.isNullType(parameterType) || parameterType instanceof PsiArrayType) continue;
      if (parameterType.isAssignableFrom(expressionType)) continue;
      PsiClass parameterClass = PsiUtil.resolveClassInType(parameterType);
      PsiClass expressionClass = PsiUtil.resolveClassInType(expressionType);
      if (parameterClass == null || expressionClass == null) continue;
      if (expressionClass instanceof PsiAnonymousClass) continue;
      if (parameterClass.isInheritor(expressionClass, true)) continue;
      QuickFixAction.registerQuickFixAction(highlightInfo, new ChangeParameterClassFix(expressionClass, (PsiClassType)parameterType));
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
