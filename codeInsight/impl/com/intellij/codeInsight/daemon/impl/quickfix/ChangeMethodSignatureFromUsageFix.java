/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 13, 2002
 * Time: 3:26:50 PM
 * To change this template use Options | File Templates.
 */
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.findUsages.FindUsagesUtil;
import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureDialog;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class ChangeMethodSignatureFromUsageFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.daemon.impl.quickfix.CastMethodParametersFix");

  private final PsiMethod myTargetMethod;
  private final PsiExpression[] myExpressions;
  private final PsiSubstitutor mySubstitutor;
  private final PsiElement myContext;
  private ParameterInfo[] myNewParametersInfo;

  ChangeMethodSignatureFromUsageFix(PsiMethod targetMethod, PsiExpression[] expressions, PsiSubstitutor substitutor, PsiElement context) {
    myTargetMethod = targetMethod;
    myExpressions = expressions;
    mySubstitutor = substitutor;
    myContext = context; LOG.assertTrue(targetMethod != null);
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("change.method.signature.from.usage.text",
                                  HighlightUtil.formatMethod(myTargetMethod),
                                  myTargetMethod.getName(),
                                  formatTypesList(myNewParametersInfo, myContext));
  }

  private static String formatTypesList(ParameterInfo[] infos, PsiElement context) {
    String result = "";
    try {
      for (ParameterInfo info : infos) {
        PsiType type = info.getTypeWrapper().getType(context, context.getManager());
        if (result.length() != 0) {
          result += ", ";
        }
        result += type.getPresentableText();
      }
    }
    catch (IncorrectOperationException e) {
      return null;
    }
    return result;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("change.method.signature.from.usage.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (!myTargetMethod.isValid()) return false;
    for (PsiExpression expression : myExpressions) {
      if (!expression.isValid()) return false;
    }

    myNewParametersInfo = getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor);
    return myNewParametersInfo != null && formatTypesList(myNewParametersInfo, myContext) != null;
  }

  public void invoke(Project project, Editor editor, final PsiFile file) {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    final PsiMethod method = SuperMethodWarningUtil.checkSuperMethod(myTargetMethod, RefactoringBundle.message("to.refactor"));
    if (method == null) return;
    if (!CodeInsightUtil.prepareFileForWrite(method.getContainingFile())) return;

    final FindUsagesOptions options = new FindUsagesOptions(project);
    options.isImplementingMethods = true;
    options.isMethodsUsages = true;
    options.isOverridingMethods = true;
    options.isUsages = true;
    options.isSearchForTextOccurences = false;
    final Ref<Integer> usagesFound = new Ref<Integer>(Integer.valueOf(0));
    Runnable runnable = new Runnable() {
      public void run() {
        Processor<UsageInfo> processor = new Processor<UsageInfo>() {
          public boolean process(final UsageInfo t) {
            usagesFound.set(Integer.valueOf(usagesFound.get().intValue() + 1));
            return false;
          }
        };
        FindUsagesUtil.processUsages(method, processor, options);
      }
    };
    String progressTitle = QuickFixBundle.message("searching.for.usages.progress.title");
    if (!ProgressManager.getInstance().runProcessWithProgressSynchronously(runnable, progressTitle, true, project)) return;

    myNewParametersInfo = getNewParametersInfo(myExpressions, myTargetMethod, mySubstitutor);
    if (usagesFound.get().intValue() <= 1) {
      ChangeSignatureProcessor processor = new ChangeSignatureProcessor(
                            project,
                            method,
                            false, null,
                            method.getName(),
                            method.getReturnType(),
                            myNewParametersInfo);
      processor.run();
      ApplicationManager.getApplication().runWriteAction(new Runnable() {
        public void run() {
          UndoManager.getInstance(file.getProject()).markDocumentForUndo(file);
        }
      });
    }
    else {
      List<ParameterInfo> parameterInfos = Arrays.asList(myNewParametersInfo);
      ChangeSignatureDialog dialog = new ChangeSignatureDialog(project, method, false);
      dialog.setParameterInfos(parameterInfos);
      dialog.show();
    }
  }

  private static ParameterInfo[] getNewParametersInfo(PsiExpression[] expressions,
                                                      PsiMethod targetMethod,
                                                      PsiSubstitutor substitutor) {
    PsiParameter[] parameters = targetMethod.getParameterList().getParameters();
    List<ParameterInfo> result = new ArrayList<ParameterInfo>();
    if (expressions.length < parameters.length) {
      // find which parameters to remove
      int ei = 0;
      int pi = 0;

      while (ei < expressions.length && pi < parameters.length) {
        PsiExpression expression = expressions[ei];
        PsiParameter parameter = parameters[pi];
        PsiType paramType = substitutor.substitute(parameter.getType());
        if (TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression)) {
          result.add(new ParameterInfo(pi, parameter.getName(), PsiUtil.convertAnonymousToBaseType(paramType)));
          pi++;
          ei++;
        }
        else {
          pi++;
        }
      }
      if (result.size() != expressions.length) return null;
    }
    else if (expressions.length > parameters.length) {
      // find which parameters to introduce and where
      Set<String> existingNames = new HashSet<String>();
      for (PsiParameter parameter : parameters) {
        existingNames.add(parameter.getName());
      }
      int ei = 0;
      int pi = 0;
      while (ei < expressions.length || pi < parameters.length) {
        PsiExpression expression = ei < expressions.length ? expressions[ei] : null;
        PsiParameter parameter = pi < parameters.length ? parameters[pi] : null;
        PsiType paramType = parameter == null ? null : substitutor.substitute(parameter.getType());
        boolean parameterAssignable = paramType != null && (expression == null || TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression));
        if (parameterAssignable) {
          result.add(new ParameterInfo(pi, parameter.getName(), paramType));
          pi++;
          ei++;
        }
        else if (expression != null) {
          PsiType exprType = RefactoringUtil.getTypeByExpression(expression);
          if (exprType == null) return null;
          CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(expression.getProject());
          String name = suggestUniqueParameterName(codeStyleManager, expression, exprType, existingNames);
          result.add(new ParameterInfo(-1, name, exprType, expression.getText()));
          ei++;
        }
      }
      if (result.size() != expressions.length) return null;
    }
    else {
      //parameter type changed
      for (int i = 0; i < parameters.length; i++) {
        PsiParameter parameter = parameters[i];
        PsiExpression expression = expressions[i];
        PsiType paramType = substitutor.substitute(parameter.getType());
        if (TypeConversionUtil.areTypesAssignmentCompatible(paramType, expression)) {
          result.add(new ParameterInfo(i, parameter.getName(), paramType));
        }
        else {
          PsiType exprType = RefactoringUtil.getTypeByExpression(expression);
          if (exprType == null) return null;
          result.add(new ParameterInfo(i, parameter.getName(), exprType));
        }
      }
      // do not perform silly refactorings
      boolean isSilly = true;
      for (int i = 0; i < result.size(); i++) {
        PsiParameter parameter = parameters[i];
        PsiType paramType = substitutor.substitute(parameter.getType());
        ParameterInfo parameterInfo = result.get(i);
        String typeText = parameterInfo.getTypeText();
        if (!paramType.equalsToText(typeText)) {
          isSilly = false;
          break;
        }
      }
      if (isSilly) return null;
    }
    return result.toArray(new ParameterInfo[result.size()]);
  }

  private static String suggestUniqueParameterName(CodeStyleManager codeStyleManager,
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

  public static void registerIntentions(JavaResolveResult[] candidates,
                                        PsiExpressionList list,
                                        HighlightInfo highlightInfo, TextRange fixRange) {
    if (candidates == null || candidates.length == 0) return;
    PsiExpression[] expressions = list.getExpressions();
    for (JavaResolveResult candidate : candidates) {
      registerIntention(expressions, highlightInfo, fixRange, candidate, list);
    }
  }

  private static void registerIntention(PsiExpression[] expressions,
                                        HighlightInfo highlightInfo,
                                        TextRange fixRange,
                                        JavaResolveResult candidate,
                                        PsiElement context) {
    if (!candidate.isStaticsScopeCorrect()) return;
    PsiMethod method = (PsiMethod)candidate.getElement();
    PsiSubstitutor substitutor = candidate.getSubstitutor();
    if (method.getManager().isInProject(method)) {
      ChangeMethodSignatureFromUsageFix fix = new ChangeMethodSignatureFromUsageFix(method, expressions, substitutor, context);
      QuickFixAction.registerQuickFixAction(highlightInfo, fixRange, fix, null, null);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }
}
