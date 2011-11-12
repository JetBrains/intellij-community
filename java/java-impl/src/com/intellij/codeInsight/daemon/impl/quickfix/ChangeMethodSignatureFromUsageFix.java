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

import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.HighPriorityAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChangeMethodSignatureFromUsageFix implements IntentionAction, HighPriorityAction {
  private final PsiMethod myTargetMethod;
  private final PsiExpression[] myExpressions;
  private final PsiSubstitutor mySubstitutor;
  private final PsiElement myContext;
  private final boolean myChangeAllUsages;
  private final int myMinUsagesNumberToShowDialog;
  private ParameterInfoImpl[] myNewParametersInfo;
  private static final Logger LOG = Logger.getInstance("#" + ChangeMethodSignatureFromUsageFix.class.getName());

  ChangeMethodSignatureFromUsageFix(@NotNull PsiMethod targetMethod,
                                    @NotNull PsiExpression[] expressions,
                                    @NotNull PsiSubstitutor substitutor,
                                    @NotNull PsiElement context,
                                    boolean changeAllUsages, int minUsagesNumberToShowDialog) {
    myTargetMethod = targetMethod;
    myExpressions = expressions;
    mySubstitutor = substitutor;
    myContext = context;
    myChangeAllUsages = changeAllUsages;
    myMinUsagesNumberToShowDialog = minUsagesNumberToShowDialog;
  }

  @Override
  @NotNull
  public String getText() {
    final String shortText = getShortText();
    if (shortText != null) return shortText;
    return QuickFixBundle.message("change.method.signature.from.usage.text",
                                  HighlightUtil.formatMethod(myTargetMethod),
                                  myTargetMethod.getName(),
                                  formatTypesList(myNewParametersInfo, myContext));
  }

  @Nullable
  private String getShortText() {
    final StringBuilder buf = new StringBuilder();
    final HashSet<ParameterInfoImpl> newParams = new HashSet<ParameterInfoImpl>();
    final HashSet<ParameterInfoImpl> removedParams = new HashSet<ParameterInfoImpl>();
    final HashSet<ParameterInfoImpl> changedParams = new HashSet<ParameterInfoImpl>();
    getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor, buf, newParams, removedParams, changedParams);

    final String targetMethodName = myTargetMethod.getName();
    if (myTargetMethod.getContainingClass().findMethodsByName(targetMethodName, true).length == 1) {
      if (newParams.size() == 1) {
        final ParameterInfoImpl p = newParams.iterator().next();
        return QuickFixBundle.message("add.parameter.from.usage.text", p.getTypeText(), (ArrayUtil.find(myNewParametersInfo, p) + 1), targetMethodName);
      }
      if (removedParams.size() == 1) {
        final ParameterInfoImpl p = removedParams.iterator().next();
        return QuickFixBundle.message("remove.parameter.from.usage.text", (p.getOldIndex() + 1), targetMethodName);
      }
      if (changedParams.size() == 1) {
        final ParameterInfoImpl p = changedParams.iterator().next();
        return QuickFixBundle.message("change.parameter.from.usage.text", (p.getOldIndex() + 1), targetMethodName, myTargetMethod.getParameterList().getParameters()[p.getOldIndex()].getType().getPresentableText(),  p.getTypeText());
      }
    }
    return "<html> Change signature of " + targetMethodName + "(" + buf.toString() + ")</html>";
  }

  private static String formatTypesList(ParameterInfoImpl[] infos, PsiElement context) {
    if (infos == null) return null;
    String result = "";
    try {
      for (ParameterInfoImpl info : infos) {
        PsiType type = info.createType(context, context.getManager());
        if (result.length() != 0) {
          result += ", ";
        }
        LOG.assertTrue(type != null, "old idx: " + info.getOldIndex() + "; " + info.getClass().getName());
        result += type.getPresentableText();
      }
    }
    catch (IncorrectOperationException e) {
      return null;
    }
    return result;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("change.method.signature.from.usage.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myTargetMethod.isValid() || myTargetMethod.getContainingClass() == null) return false;
    for (PsiExpression expression : myExpressions) {
      if (!expression.isValid()) return false;
    }

    myNewParametersInfo = getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor);
    if (myNewParametersInfo == null || formatTypesList(myNewParametersInfo, myContext) == null) return false;
    return !isMethodSignatureExists();
  }

  public boolean isMethodSignatureExists() {
    PsiClass target = myTargetMethod.getContainingClass();
    LOG.assertTrue(target != null);
    PsiMethod[] methods = target.findMethodsByName(myTargetMethod.getName(), false);
    for (PsiMethod method : methods) {
      if (PsiUtil.isApplicable(method, PsiSubstitutor.EMPTY, myExpressions)) return true;
    }
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, final PsiFile file) {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;

    final PsiMethod method = SuperMethodWarningUtil.checkSuperMethod(myTargetMethod, RefactoringBundle.message("to.refactor"));
    if (method == null) return;
    if (!CodeInsightUtilBase.prepareFileForWrite(method.getContainingFile())) return;

    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    final FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(method, false);
    if (handler == null) return; //on failure or cancel (e.g. cancel of super methods dialog)

    final JavaMethodFindUsagesOptions options = new JavaMethodFindUsagesOptions(project);
    options.isImplementingMethods = true;
    options.isOverridingMethods = true;
    options.isUsages = true;
    options.isSearchForTextOccurrences = false;
    final int[] usagesFound = new int[1];
    Runnable runnable = new Runnable() {
      @Override
      public void run() {
        Processor<UsageInfo> processor = new Processor<UsageInfo>() {
          @Override
          public boolean process(final UsageInfo t) {
            return ++usagesFound[0] < myMinUsagesNumberToShowDialog;
          }
        };

        handler.processElementUsages(method, processor, options);
      }
    };
    String progressTitle = QuickFixBundle.message("searching.for.usages.progress.title");
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, progressTitle, true, project)) return;

    myNewParametersInfo = getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor);
    if (ApplicationManager.getApplication().isUnitTestMode() || usagesFound[0] < myMinUsagesNumberToShowDialog) {
      ChangeSignatureProcessor processor = new ChangeSignatureProcessor(
                            project,
                            method,
                            false, null,
                            method.getName(),
                            method.getReturnType(),
                            myNewParametersInfo){
        @Override
        @NotNull
        protected UsageInfo[] findUsages() {
          return myChangeAllUsages ? super.findUsages() : UsageInfo.EMPTY_ARRAY;
        }
      };
      processor.run();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        @Override
        public void run() {
          UndoUtil.markPsiFileForUndo(file);
        }
      });
    }
    else {
      List<ParameterInfoImpl> parameterInfos = myNewParametersInfo != null
                                               ? new ArrayList<ParameterInfoImpl>(Arrays.asList(myNewParametersInfo))
                                               : new ArrayList<ParameterInfoImpl>();
      final PsiReferenceExpression refExpr = TargetElementUtil.findReferenceExpression(editor);
      JavaChangeSignatureDialog dialog = new JavaChangeSignatureDialog(project, method, allowDelegate(), refExpr);
      dialog.setParameterInfos(parameterInfos);
      dialog.show();
      List<ParameterInfoImpl> parameters = dialog.getParameters();
      myNewParametersInfo = parameters.toArray(new ParameterInfoImpl[parameters.size()]);
    }
  }

  protected boolean allowDelegate() {
    return false;
  }

  public String getNewParameterNameByOldIndex(int oldIndex) {
    if (myNewParametersInfo == null) return null;
    for (ParameterInfoImpl info : myNewParametersInfo) {
      if (info.oldParameterIndex == oldIndex) {
        return info.getName();
      }
    }
    return null;
  }

  private static ParameterInfoImpl[] getNewParametersInfo(PsiExpression[] expressions,
                                                          PsiMethod targetMethod,
                                                          PsiSubstitutor substitutor) {
    return getNewParametersInfo(expressions, targetMethod, substitutor, new StringBuilder(), new HashSet<ParameterInfoImpl>(), new HashSet<ParameterInfoImpl>(), new HashSet<ParameterInfoImpl>());
  }

  private static ParameterInfoImpl[] getNewParametersInfo(PsiExpression[] expressions,
                                                          PsiMethod targetMethod,
                                                          PsiSubstitutor substitutor,
                                                          final StringBuilder buf,
                                                          final HashSet<ParameterInfoImpl> newParams,
                                                          final HashSet<ParameterInfoImpl> removedParams,
                                                          final HashSet<ParameterInfoImpl> changedParams) {
    PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
    List<ParameterInfoImpl> result = new ArrayList<ParameterInfoImpl>();
    if (expressions.length < parameters.length) {
      // find which parameters to remove
      int ei = 0;
      int pi = 0;

      while (ei < expressions.length && pi < parameters.length) {
        PsiExpression expression = expressions[ei];
        PsiParameter parameter = parameters[pi];
        PsiType paramType = substitutor.substitute(parameter.getType());
        if (buf.length() > 0) buf.append(", ");
        final PsiType parameterType = PsiUtil.convertAnonymousToBaseType(paramType);
        final String presentableText = parameterType.getPresentableText();
        final ParameterInfoImpl parameterInfo = new ParameterInfoImpl(pi, parameter.getName(), parameterType);
        if (TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression)) {
          buf.append(presentableText);
          result.add(parameterInfo);
          pi++;
          ei++;
        }
        else {
          buf.append("<s>").append(presentableText).append("</s>");
          removedParams.add(parameterInfo);
          pi++;
        }
      }
      if (result.size() != expressions.length) return null;
      for(int i = pi; i < parameters.length; i++) {
        if (buf.length() > 0) buf.append(", ");
        buf.append("<s>").append(parameters[i].getType().getPresentableText()).append("</s>");
        final ParameterInfoImpl parameterInfo = new ParameterInfoImpl(pi, parameters[i].getName(), parameters[i].getType());
        removedParams.add(parameterInfo);
      }
    }
    else if (expressions.length > parameters.length) {
      // find which parameters to introduce and where
      Set<String> existingNames = new HashSet<String>();
      for (PsiParameter parameter : parameters) {
        existingNames.add(parameter.getName());
      }
      int ei = 0;
      int pi = 0;
      PsiParameter varargParam = targetMethod.isVarArgs() ? parameters[parameters.length - 1] : null;
      while (ei < expressions.length || pi < parameters.length) {
        if (buf.length() > 0) buf.append(", ");
        PsiExpression expression = ei < expressions.length ? expressions[ei] : null;
        PsiParameter parameter = pi < parameters.length ? parameters[pi] : null;
        PsiType paramType = parameter == null ? null : substitutor.substitute(parameter.getType());
        boolean parameterAssignable = paramType != null && (expression == null || TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression));
        if (parameterAssignable) {
          final PsiType type = parameter.getType();
          result.add(new ParameterInfoImpl(pi, parameter.getName(), type));
          buf.append(type.getPresentableText());
          pi++;
          ei++;
        }
        else if (isArgumentInVarargPosition(expressions, ei, varargParam, substitutor)) {
          if (pi == parameters.length - 1) {
            assert varargParam != null;
            final PsiType type = varargParam.getType();
            result.add(new ParameterInfoImpl(pi, varargParam.getName(), type));
            buf.append(type.getPresentableText());
          }
          pi++;
          ei++;
        }
        else if (expression != null) {
          if (varargParam != null && pi >= parameters.length) return null;
          PsiType exprType = RefactoringUtil.getTypeByExpression(expression);
          if (exprType == null) return null;
          JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
          String name = suggestUniqueParameterName(codeStyleManager, expression, exprType, existingNames);
          final ParameterInfoImpl newParameterInfo = new ParameterInfoImpl(-1, name, exprType, expression.getText().replace('\n', ' '));
          result.add(newParameterInfo);
          newParams.add(newParameterInfo);
          buf.append("<b>").append(exprType.getPresentableText()).append("</b>");
          ei++;
        }
      }
      if (result.size() != expressions.length && varargParam == null) return null;
    }
    else {
      //parameter type changed
      for (int i = 0; i < parameters.length; i++) {
        if (buf.length() > 0) buf.append(", ");
        PsiParameter parameter = parameters[i];
        PsiExpression expression = expressions[i];
        PsiType paramType = substitutor.substitute(parameter.getType());
        final String presentableText = paramType.getPresentableText();
        if (TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression)) {
          result.add(new ParameterInfoImpl(i, parameter.getName(), paramType));
          buf.append(presentableText);
        }
        else {
          PsiType exprType = RefactoringUtil.getTypeByExpression(expression);
          if (exprType == null) return null;
          final ParameterInfoImpl changedParameterInfo = new ParameterInfoImpl(i, parameter.getName(), exprType);
          result.add(changedParameterInfo);
          changedParams.add(changedParameterInfo);
          buf.append("<s>").append(presentableText).append("</s> <b>").append(exprType.getPresentableText()).append("</b>");
        }
      }
      // do not perform silly refactorings
      boolean isSilly = true;
      for (int i = 0; i < result.size(); i++) {
        PsiParameter parameter = parameters[i];
        PsiType paramType = substitutor.substitute(parameter.getType());
        ParameterInfoImpl parameterInfo = result.get(i);
        String typeText = parameterInfo.getTypeText();
        if (!paramType.equalsToText(typeText)) {
          isSilly = false;
          break;
        }
      }
      if (isSilly) return null;
    }
    return result.toArray(new ParameterInfoImpl[result.size()]);
  }

  private static boolean isArgumentInVarargPosition(PsiExpression[] expressions, int ei, PsiParameter varargParam, PsiSubstitutor substitutor) {
    if (varargParam == null) return false;
    final PsiExpression expression = expressions[ei];
    if (expression == null || TypeConversionUtil.areTypesAssignmentCompatible(substitutor.substitute(((PsiEllipsisType)varargParam.getType()).getComponentType()), expression)) {
      final int lastExprIdx = expressions.length - 1;
      if (ei == lastExprIdx) return true;
      return expressions[lastExprIdx].getType() != PsiType.NULL;
    }
    return false;
  }

  private static String suggestUniqueParameterName(JavaCodeStyleManager codeStyleManager,
                                                              PsiExpression expression,
                                                              PsiType exprType,
                                                              Set<String> existingNames) {
    SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, expression, exprType);
    @NonNls String[] names = nameInfo.names;
    if (names.length == 0) names = new String[]{"param"};
    int suffix = 0;
    while (true) {
      for (String name : names) {
        String suggested = name + (suffix == 0 ? "" : String.valueOf(suffix));
        if (existingNames.add(suggested)) {
          return suggested;
        }
      }
      suffix++;
    }
  }

  public static void registerIntentions(@NotNull JavaResolveResult[] candidates,
                                        @NotNull PsiExpressionList list,
                                        @NotNull HighlightInfo highlightInfo,
                                        TextRange fixRange) {
    if (candidates.length == 0) return;
    PsiExpression[] expressions = list.getExpressions();
    for (JavaResolveResult candidate : candidates) {
      registerIntention(expressions, highlightInfo, fixRange, candidate, list);
    }
  }

  private static void registerIntention(@NotNull PsiExpression[] expressions,
                                        @NotNull HighlightInfo highlightInfo,
                                        TextRange fixRange,
                                        @NotNull JavaResolveResult candidate,
                                        @NotNull PsiElement context) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (method != null && context.getManager().isInProject(method)) {
      ChangeMethodSignatureFromUsageFix fix = new ChangeMethodSignatureFromUsageFix(method, expressions, substitutor, context, false, 2);
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, fix, null);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
