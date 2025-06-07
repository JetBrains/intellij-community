// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.JavaTargetElementEvaluator;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.JavaHighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.diagnostic.PluginException;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.findUsages.JavaMethodFindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.source.resolve.graphInference.PsiPolyExpressionUtil;
import com.intellij.psi.util.*;
import com.intellij.refactoring.ChangeSignatureRefactoring;
import com.intellij.refactoring.JavaRefactoringFactory;
import com.intellij.refactoring.changeSignature.JavaChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ChangeMethodSignatureFromUsageFix implements IntentionAction/*, HighPriorityAction*/ {
  final PsiMethod myTargetMethod;
  final PsiExpression[] myExpressions;
  final PsiSubstitutor mySubstitutor;
  final PsiElement myContext;
  private final boolean myChangeAllUsages;
  private final int myMinUsagesNumberToShowDialog;
  ParameterInfoImpl[] myNewParametersInfo;
  private @IntentionName String myShortName;
  private static final Logger LOG = Logger.getInstance(ChangeMethodSignatureFromUsageFix.class);

  public ChangeMethodSignatureFromUsageFix(@NotNull PsiMethod targetMethod,
                                           PsiExpression @NotNull [] expressions,
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
  public @NotNull String getText() {
    final String shortText = myShortName;
    if (shortText != null) {
      return shortText;
    }
    return QuickFixBundle.message("change.method.signature.from.usage.text",
                                  JavaHighlightUtil.formatMethod(myTargetMethod),
                                  myTargetMethod.getName(),
                                  formatTypesList(myNewParametersInfo, myContext));
  }

  private @IntentionName String getShortText(final StringBuilder buf,
                                             final HashSet<? extends ParameterInfoImpl> newParams,
                                             final HashSet<? extends ParameterInfoImpl> removedParams,
                                             final HashSet<? extends ParameterInfoImpl> changedParams) {
    final String targetMethodName = myTargetMethod.getName();
    PsiClass aClass = myTargetMethod.getContainingClass();
    if (aClass != null && aClass.findMethodsByName(targetMethodName, true).length == 1) {
      JavaElementKind parameter = JavaElementKind.PARAMETER;
      JavaElementKind method = JavaElementKind.fromElement(myTargetMethod);
      if (JavaPsiRecordUtil.isCanonicalConstructor(myTargetMethod)) {
        parameter = JavaElementKind.RECORD_COMPONENT;
        method = JavaElementKind.RECORD;
      }
      if (newParams.size() == 1) {
        final ParameterInfoImpl p = newParams.iterator().next();
        return QuickFixBundle
          .message("add.parameter.from.usage.text", p.getTypeText(), ArrayUtil.find(myNewParametersInfo, p) + 1, 
                   parameter.object(), method.object(), targetMethodName);
      }
      if (removedParams.size() == 1) {
        final ParameterInfoImpl p = removedParams.iterator().next();
        return QuickFixBundle.message("remove.parameter.from.usage.text", p.getOldIndex() + 1, 
                                      parameter.object(), method.object(), targetMethodName);
      }
      if (changedParams.size() == 1) {
        final ParameterInfoImpl p = changedParams.iterator().next();
        return QuickFixBundle.message("change.parameter.from.usage.text", p.getOldIndex() + 1,
                                      parameter.object(), method.object(), targetMethodName,
                                      Objects.requireNonNull(myTargetMethod.getParameterList().getParameter(p.getOldIndex())).getType().getPresentableText(),
                                      p.getTypeText());
      }
    }
    return JavaBundle.message("change.signature.from.usage.short.name", targetMethodName, buf);
  }

  private static @Nullable String formatTypesList(ParameterInfoImpl[] infos, PsiElement context) {
    if (infos == null) return null;
    StringBuilder result = new StringBuilder();
    try {
      for (ParameterInfoImpl info : infos) {
        PsiType type = info.createType(context);
        if (type == null) return null;
        if (!result.isEmpty()) result.append(", ");
        result.append(type.getPresentableText());
      }
      return result.toString();
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("change.method.signature.from.usage.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
    if (!myTargetMethod.isValid() || myTargetMethod.getContainingClass() == null) return false;
    if (ContainerUtil.exists(myTargetMethod.getParameterList().getParameters(), p -> !p.isValid())) {
      return false;
    }
    for (PsiExpression expression : myExpressions) {
      if (!expression.isValid()) return false;
    }
    if (!mySubstitutor.isValid()) return false;
    if (myTargetMethod.isConstructor()) {
      PsiCallExpression call = PsiTreeUtil.getParentOfType(myContext, PsiCallExpression.class);
      if (call instanceof PsiMethodCallExpression && ((PsiMethodCallExpression)call).getMethodExpression().textMatches("this") &&
          PsiTreeUtil.isAncestor(myTargetMethod, call, true)) {
        // Avoid creating recursive constructor call
        return false;
      }
    }

    final StringBuilder buf = new StringBuilder();
    final HashSet<ParameterInfoImpl> newParams = new HashSet<>();
    final HashSet<ParameterInfoImpl> removedParams = new HashSet<>();
    final HashSet<ParameterInfoImpl> changedParams = new HashSet<>();
    myNewParametersInfo = getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor, buf, newParams, removedParams, changedParams);
    if (myNewParametersInfo == null || formatTypesList(myNewParametersInfo, myContext) == null) return false;
    myShortName = getShortText(buf, newParams, removedParams, changedParams);
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
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    HashSet<ParameterInfoImpl> newParams = new HashSet<>();
    HashSet<ParameterInfoImpl> removedParams = new HashSet<>();
    HashSet<ParameterInfoImpl> changedParams = new HashSet<>();
    if (!(myTargetMethod.getContainingFile() instanceof PsiJavaFile)) return IntentionPreviewInfo.EMPTY;
    ParameterInfoImpl[] parameterInfos =
      getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor, new StringBuilder(), newParams, removedParams, changedParams);
    PsiParameterList parameterList = myTargetMethod.getParameterList();
    PsiParameter[] oldParameters = parameterList.getParameters();
    if (parameterInfos == null) return IntentionPreviewInfo.EMPTY;
    String params = "(" + StringUtil.join(parameterInfos, p -> {
      if (p.oldParameterIndex != ParameterInfo.NEW_PARAMETER && !changedParams.contains(p)) {
        PsiParameter parameter = oldParameters[p.oldParameterIndex];
        PsiRecordComponent component = JavaPsiRecordUtil.getComponentForCanonicalConstructorParameter(parameter);
        return Objects.requireNonNullElse(component, parameter).getText();
      }
      return p.getTypeText() + " " + p.getName();
    }, ", ") + ")";
    TextRange range;
    String methodText;
    PsiRecordHeader header = null;
    if (JavaPsiRecordUtil.isCanonicalConstructor(myTargetMethod) && !JavaPsiRecordUtil.isExplicitCanonicalConstructor(myTargetMethod)) {
      PsiClass recordClass = ObjectUtils.tryCast(myTargetMethod.getParent(), PsiClass.class);
      header = recordClass == null ? null : recordClass.getRecordHeader();
    }
    if (header != null) {
      range = header.getTextRangeInParent();
      methodText = header.getParent().getText();
    } else {
      if (myTargetMethod instanceof SyntheticElement) return IntentionPreviewInfo.EMPTY;
      range = parameterList.getTextRangeInParent();
      methodText = myTargetMethod.getText();
    }
    String methodTextWithChangedParameters = methodText.substring(0, range.getStartOffset()) + params + methodText.substring(range.getEndOffset());
    return new IntentionPreviewInfo.CustomDiff(JavaFileType.INSTANCE,
                                               myTargetMethod.getContainingFile() != myContext.getContainingFile() ? myTargetMethod.getContainingFile().getName() : null,
                                               methodText,
                                               methodTextWithChangedParameters); 
  }

  @Override
  public void invoke(final @NotNull Project project, Editor editor, final PsiFile psiFile) {
    if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;

    final PsiMethod method = SuperMethodWarningUtil.checkSuperMethod(myTargetMethod);
    if (method == null) return;
    myNewParametersInfo = getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor);

    final List<ParameterInfoImpl> parameterInfos =
      performChange(project, editor, psiFile, method, myMinUsagesNumberToShowDialog, myNewParametersInfo, myChangeAllUsages, false, null);
    if (parameterInfos != null) {
      myNewParametersInfo = parameterInfos.toArray(new ParameterInfoImpl[0]);
    }
  }

  static List<ParameterInfoImpl> performChange(@NotNull Project project,
                                               final Editor editor,
                                               final PsiFile psiFile,
                                               @NotNull PsiMethod method,
                                               final int minUsagesNumber,
                                               final ParameterInfoImpl[] newParametersInfo,
                                               final boolean changeAllUsages,
                                               final boolean allowDelegation,
                                               final @Nullable Consumer<? super List<ParameterInfo>> callback) {
    if (!FileModificationService.getInstance().prepareFileForWrite(method.getContainingFile())) return null;
    final FindUsagesManager findUsagesManager = ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager();
    final FindUsagesHandler handler = findUsagesManager.getFindUsagesHandler(method, false);
    if (handler == null) return null;//on failure or cancel (e.g. cancel of super methods dialog)

    final JavaMethodFindUsagesOptions options = new JavaMethodFindUsagesOptions(project);
    options.isImplementingMethods = true;
    options.isOverridingMethods = true;
    options.isUsages = true;
    options.isSearchForTextOccurrences = false;
    final int[] usagesFound = new int[1];
    Runnable runnable = () -> {
      Processor<UsageInfo> processor = t -> ++usagesFound[0] < minUsagesNumber;

      handler.processElementUsages(method, processor, options);
    };
    String progressTitle = QuickFixBundle.message("searching.for.usages.progress.title");
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, progressTitle, true, project)) return null;

    if (ApplicationManager.getApplication().isUnitTestMode() || usagesFound[0] < minUsagesNumber) {
      ChangeSignatureRefactoring processor =
        JavaRefactoringFactory.getInstance(project)
          .createChangeSignatureProcessor(method, false, null, method.getName(), method.getReturnType(), newParametersInfo, null, null,
                                          null, callback);
      processor.run();
      ApplicationManager.getApplication().runWriteAction(() -> UndoUtil.markPsiFileForUndo(psiFile));
      return Arrays.asList(newParametersInfo);
    }
    else {
      final List<ParameterInfoImpl> parameterInfos = newParametersInfo != null
                                                     ? new ArrayList<>(Arrays.asList(newParametersInfo))
                                                     : new ArrayList<>();
      final PsiReferenceExpression refExpr = JavaTargetElementEvaluator.findReferenceExpression(editor);
      JavaChangeSignatureDialog dialog = JavaChangeSignatureDialog.createAndPreselectNew(project, method, parameterInfos, allowDelegation, refExpr, callback);
      dialog.setParameterInfos(parameterInfos);
      dialog.show();
      return dialog.isOK() ? dialog.getParameters() : null;
    }
  }

  protected ParameterInfoImpl @Nullable [] getNewParametersInfo(PsiExpression[] expressions,
                                                                PsiMethod targetMethod,
                                                                PsiSubstitutor substitutor) {
    return getNewParametersInfo(expressions, targetMethod, substitutor, new StringBuilder(), new HashSet<>(),
                                new HashSet<>(),
                                new HashSet<>());
  }

  private ParameterInfoImpl @Nullable [] getNewParametersInfo(PsiExpression[] expressions,
                                                              PsiMethod targetMethod,
                                                              PsiSubstitutor substitutor,
                                                              final @NonNls StringBuilder buf,
                                                              final HashSet<? super ParameterInfoImpl> newParams,
                                                              final HashSet<? super ParameterInfoImpl> removedParams,
                                                              final HashSet<? super ParameterInfoImpl> changedParams) {
    PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
    List<ParameterInfoImpl> result = new ArrayList<>();
    if (expressions.length < parameters.length) {
      // find which parameters to remove
      int ei = 0;
      int pi = 0;

      while (ei < expressions.length && pi < parameters.length) {
        PsiExpression expression = expressions[ei];
        PsiParameter parameter = parameters[pi];
        PsiType bareParameterType = getValidParameterType(parameter, targetMethod);
        PsiType paramType = substitutor.substitute(bareParameterType);
        if (!buf.isEmpty()) buf.append(", ");
        final PsiType parameterType = PsiUtil.convertAnonymousToBaseType(paramType);
        final String presentableText = escapePresentableType(parameterType);
        final ParameterInfoImpl parameterInfo = ParameterInfoImpl.create(pi).withName(parameter.getName()).withType(bareParameterType);
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
        if (!buf.isEmpty()) buf.append(", ");
        PsiType paramType = getValidParameterType(parameters[i], targetMethod);
        buf.append("<s>").append(escapePresentableType(paramType)).append("</s>");
        final ParameterInfoImpl parameterInfo = ParameterInfoImpl.create(pi)
          .withName(parameters[i].getName())
          .withType(paramType);
        removedParams.add(parameterInfo);
      }
    }
    else if (expressions.length > parameters.length) {
      if (!findNewParamsPlace(expressions, targetMethod, substitutor, buf, newParams, parameters, result)) return null;
    }
    else {
      //parameter type changed
      for (int i = 0; i < parameters.length; i++) {
        if (!buf.isEmpty()) buf.append(", ");
        PsiParameter parameter = parameters[i];
        PsiExpression expression = expressions[i];
        PsiType bareParamType = getValidParameterType(parameter, targetMethod);
        PsiType paramType = substitutor.substitute(bareParamType);
        PsiUtil.ensureValidType(paramType);
        final String presentableText = escapePresentableType(paramType);
        if (TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression)) {
          result.add(ParameterInfoImpl.create(i).withName(parameter.getName()).withType(paramType));
          buf.append(presentableText);
        }
        else {
          if (PsiPolyExpressionUtil.isPolyExpression(expression)) return null;
          PsiType exprType = CommonJavaRefactoringUtil.getTypeByExpression(expression);
          if (exprType == null || PsiTypes.voidType().equals(exprType)) return null;
          exprType = PsiTypesUtil.removeExternalAnnotations(exprType);
          if (exprType instanceof PsiDisjunctionType disjunctionType) {
            exprType = disjunctionType.getLeastUpperBound();
          }
          if (!PsiTypesUtil.allTypeParametersResolved(myTargetMethod, exprType)) return null;
          final ParameterInfoImpl changedParameterInfo = ParameterInfoImpl.create(i).withName(parameter.getName()).withType(exprType);
          result.add(changedParameterInfo);
          changedParams.add(changedParameterInfo);
          buf.append("<s>").append(presentableText).append("</s> <b>").append(escapePresentableType(exprType)).append("</b>");
        }
      }
      // do not perform silly refactorings
      boolean isSilly = true;
      for (int i = 0; i < result.size(); i++) {
        PsiParameter parameter = parameters[i];
        PsiType paramType = substitutor.substitute(getValidParameterType(parameter, targetMethod));
        ParameterInfoImpl parameterInfo = result.get(i);
        String typeText = parameterInfo.getTypeText();
        if (!paramType.getPresentableText(true).equals(typeText)) {
          isSilly = false;
          break;
        }
      }
      if (isSilly) return null;
    }
    return result.toArray(new ParameterInfoImpl[0]);
  }

  private static @NotNull PsiType getValidParameterType(PsiParameter parameter, PsiMethod targetMethod) {
    PsiType bareParamType = parameter.getType();
    if (!bareParamType.isValid()) {
      try {
        PsiUtil.ensureValidType(bareParamType);
      }
      catch (ProcessCanceledException e) {
        throw e;
      }
      catch (Throwable e) {
        throw PluginException.createByClass(
          parameter.getClass() + "; valid=" + parameter.isValid() + "; method.valid=" + targetMethod.isValid(),
          e, parameter.getClass());
      }
    }
    return bareParamType;
  }

  protected static @NotNull String escapePresentableType(@NotNull PsiType exprType) {
    return StringUtil.escapeXmlEntities(exprType.getPresentableText());
  }

  protected boolean findNewParamsPlace(PsiExpression[] expressions,
                                       PsiMethod targetMethod,
                                       PsiSubstitutor substitutor,
                                       StringBuilder buf,
                                       HashSet<? super ParameterInfoImpl> newParams,
                                       PsiParameter[] parameters,
                                       List<? super ParameterInfoImpl> result) {
    // find which parameters to introduce and where
    Set<String> existingNames = new HashSet<>();
    for (PsiParameter parameter : parameters) {
      existingNames.add(parameter.getName());
    }
    int ei = 0;
    int pi = 0;
    PsiParameter varargParam = targetMethod.isVarArgs() ? parameters[parameters.length - 1] : null;
    while (ei < expressions.length || pi < parameters.length) {
      if (!buf.isEmpty()) buf.append(", ");
      PsiExpression expression = ei < expressions.length ? expressions[ei] : null;
      PsiParameter parameter = pi < parameters.length ? parameters[pi] : null;
      PsiType paramType = parameter == null ? null : substitutor.substitute(parameter.getType());
      boolean parameterAssignable = paramType != null && (expression == null || TypeConversionUtil
        .areTypesAssignmentCompatible(paramType, expression));
      if (parameterAssignable) {
        final PsiType type = parameter.getType();
        result.add(ParameterInfoImpl.create(pi).withName(parameter.getName()).withType(type));
        buf.append(escapePresentableType(type));
        pi++;
        ei++;
      }
      else if (isArgumentInVarargPosition(expressions, ei, varargParam, substitutor)) {
        if (pi == parameters.length - 1) {
          final PsiType type = varargParam.getType();
          result.add(ParameterInfoImpl.create(pi).withName(varargParam.getName()).withType(type));
          buf.append(escapePresentableType(type));
        }
        pi++;
        ei++;
      }
      else if (expression != null) {
        if (varargParam != null && pi >= parameters.length) return false;
        if (PsiPolyExpressionUtil.isPolyExpression(expression)) return false;
        PsiType exprType = CommonJavaRefactoringUtil.getTypeByExpression(expression);
        if (exprType == null || PsiTypes.voidType().equals(exprType)) return false;
        if (exprType instanceof PsiDisjunctionType disjunctionType) {
          exprType = disjunctionType.getLeastUpperBound();
        }
        exprType = PsiTypesUtil.removeExternalAnnotations(exprType);
        if (!PsiTypesUtil.allTypeParametersResolved(myTargetMethod, exprType)) return false;
        JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(expression.getProject());
        String name = suggestUniqueParameterName(codeStyleManager, expression, exprType, existingNames);
        final ParameterInfoImpl newParameterInfo = ParameterInfoImpl.createNew()
          .withName(name)
          .withType(exprType)
          .withDefaultValue(expression.getText().replace('\n', ' '));
        result.add(newParameterInfo);
        newParams.add(newParameterInfo);
        buf.append("<b>").append(escapePresentableType(exprType)).append("</b>");
        ei++;
      }
    }
    if (result.size() != expressions.length && varargParam == null) return false;
    return true;
  }

  static boolean isArgumentInVarargPosition(PsiExpression[] expressions, int ei, PsiParameter varargParam, PsiSubstitutor substitutor) {
    if (varargParam == null) return false;
    final PsiExpression expression = expressions[ei];
    if (expression == null || TypeConversionUtil.areTypesAssignmentCompatible(substitutor.substitute(((PsiEllipsisType)varargParam.getType()).getComponentType()), expression)) {
      final int lastExprIdx = expressions.length - 1;
      if (ei == lastExprIdx) return true;
      return expressions[lastExprIdx].getType() != PsiTypes.nullType();
    }
    return false;
  }

  static String suggestUniqueParameterName(JavaCodeStyleManager codeStyleManager,
                                           PsiExpression expression,
                                           PsiType exprType,
                                           Set<? super String> existingNames) {
    SuggestedNameInfo nameInfo = codeStyleManager.suggestVariableName(VariableKind.PARAMETER, null, expression, exprType);
    @NonNls String[] names = nameInfo.names;
    if (expression instanceof PsiReferenceExpression) {
      final PsiElement resolve = ((PsiReferenceExpression)expression).resolve();
      if (resolve instanceof PsiVariable) {
        final VariableKind variableKind = codeStyleManager.getVariableKind((PsiVariable)resolve);
        final String propertyName = codeStyleManager.variableNameToPropertyName(((PsiVariable)resolve).getName(), variableKind);
        final String parameterName = codeStyleManager.propertyNameToVariableName(propertyName, VariableKind.PARAMETER);
        names = ArrayUtil.mergeArrays(new String[]{parameterName}, names);
      }
    }
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

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
