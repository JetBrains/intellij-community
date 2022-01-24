// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.ExpressionConverter;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.javadoc.PsiDocTag;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.util.FieldConflictsResolver;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.refactoring.util.javadoc.MethodJavaDocHelper;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import it.unimi.dsi.fastutil.ints.IntList;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntConsumer;

/**
 * @author Maxim.Medvedev
 */
public final class JavaIntroduceParameterMethodUsagesProcessor implements IntroduceParameterMethodUsagesProcessor {
  private static final Logger LOG =
    Logger.getInstance(JavaIntroduceParameterMethodUsagesProcessor.class);

  private static boolean isJavaUsage(UsageInfo usage) {
    PsiElement e = usage.getElement();
    return e != null && e.getLanguage().is(JavaLanguage.INSTANCE);
  }

  @Override
  public boolean isMethodUsage(UsageInfo usage) {
    return RefactoringUtil.isMethodUsage(usage.getElement()) && isJavaUsage(usage);
  }

  @Override
  public boolean processChangeMethodUsage(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    PsiElement ref = usage.getElement();
    if (ref instanceof PsiMethodReferenceExpression) {
      final PsiExpression callExpression = LambdaRefactoringUtil.convertToMethodCallInLambdaBody((PsiMethodReferenceExpression)ref);
      if (callExpression == null) {
        return true;
      }
      ref = callExpression;
    }
    else if (!isMethodUsage(usage)) {
      return true;
    }
    PsiCall callExpression = RefactoringUtil.getCallExpressionByMethodReference(ref);
    PsiExpressionList argList = RefactoringUtil.getArgumentListByMethodReference(ref);
    if (argList == null || callExpression == null) return true;
    PsiExpression[] oldArgs = argList.getExpressions();
    JavaResolveResult result = callExpression.resolveMethodGenerics();
    boolean varargs = result instanceof MethodCandidateInfo &&
    ((MethodCandidateInfo)result).getApplicabilityLevel() == MethodCandidateInfo.ApplicabilityLevel.VARARGS;


    final PsiExpression anchor;
    final PsiMethod methodToSearchFor = data.getMethodToSearchFor();
    if (!methodToSearchFor.isVarArgs()) {
      anchor = getLast(oldArgs);
    }
    else {
      final PsiParameter[] parameters = methodToSearchFor.getParameterList().getParameters();
      if (parameters.length > oldArgs.length) {
        anchor = getLast(oldArgs);
      }
      else {
        LOG.assertTrue(parameters.length > 0);
        final int lastNonVararg = parameters.length - 2;
        anchor = lastNonVararg >= 0 ? oldArgs[lastNonVararg] : null;
      }
    }

    //if we insert parameter in method usage which is contained in method in which we insert this parameter too, we must insert parameter name instead of its initializer
    PsiMethod method = PsiTreeUtil.getParentOfType(argList, PsiMethod.class);
    if (method != null && IntroduceParameterUtil.isMethodInUsages(data, method, usages)) {
      argList
        .addAfter(JavaPsiFacade.getElementFactory(data.getProject()).createExpressionFromText(data.getParameterName(), argList), anchor);
    }
    else {
      PsiElement initializer =
        ExpressionConverter.getExpression(data.getParameterInitializer().getExpression(), JavaLanguage.INSTANCE, data.getProject());
      assert initializer instanceof PsiExpression;
      if (initializer instanceof PsiNewExpression) {
        if (!PsiDiamondTypeUtil.canChangeContextForDiamond((PsiNewExpression)initializer, ((PsiNewExpression)initializer).getType())) {
          initializer = PsiDiamondTypeUtil.expandTopLevelDiamondsInside((PsiNewExpression)initializer);
        }
      }
      substituteTypeParametersInInitializer(initializer, callExpression, methodToSearchFor);
      ChangeContextUtil.encodeContextInfo(initializer, true);
      PsiExpression newArg = (PsiExpression)argList.addAfter(initializer, anchor);
      ChangeContextUtil.decodeContextInfo(newArg, null, null);
      ChangeContextUtil.clearContextInfo(initializer);

      // here comes some postprocessing...
      new OldReferenceResolver(callExpression, newArg, data.getMethodToReplaceIn(), data.getReplaceFieldsWithGetters(), initializer)
        .resolve(varargs);
    }


    final PsiExpressionList argumentList = callExpression.getArgumentList();
    LOG.assertTrue(argumentList != null, callExpression.getText());
    removeParametersFromCall(argumentList, data.getParameterListToRemove(), methodToSearchFor);
    return false;
  }

  private static void substituteTypeParametersInInitializer(PsiElement initializer,
                                                            PsiCall callExpression,
                                                            PsiMethod method) {
    final Project project = method.getProject();
    final PsiSubstitutor psiSubstitutor = callExpression.resolveMethodGenerics().getSubstitutor();
    RefactoringUtil.replaceMovedMemberTypeParameters(initializer, PsiUtil.typeParametersIterable(method), psiSubstitutor,
                                                     JavaPsiFacade.getElementFactory(project));
  }

  private static void removeParametersFromCall(@NotNull final PsiExpressionList argList, IntList parametersToRemove, PsiMethod method) {
    final int parametersCount = method.getParameterList().getParametersCount();
    final PsiExpression[] exprs = argList.getExpressions();
    for (int i = parametersToRemove.size() - 1; i >= 0; i--) {
      int paramNum = parametersToRemove.getInt(i);
      try {
        //parameter was introduced before varargs
        if (method.isVarArgs() && paramNum == parametersCount - 1) {
          for (int j = paramNum + 1; j < exprs.length; j++) {
            exprs[j].delete();
          }
        }
        else if (paramNum < exprs.length) {
          exprs[paramNum].delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }

  @Nullable
  private static PsiExpression getLast(PsiExpression[] oldArgs) {
    PsiExpression anchor;
    if (oldArgs.length > 0) {
      anchor = oldArgs[oldArgs.length - 1];
    }
    else {
      anchor = null;
    }
    return anchor;
  }


  @Override
  public void findConflicts(IntroduceParameterData data, UsageInfo[] usages, final MultiMap<PsiElement, @Nls String> conflicts) {
    final PsiMethod method = data.getMethodToReplaceIn();
    final int parametersCount = method.getParameterList().getParametersCount();
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element instanceof PsiMethodReferenceExpression && !ApplicationManager.getApplication().isUnitTestMode()) {
        conflicts.putValue(element, JavaRefactoringBundle.message("expand.method.reference.warning"));
      }
      if (!isMethodUsage(usage)) continue;
      final PsiCall call = RefactoringUtil.getCallExpressionByMethodReference(element);
      final PsiExpressionList argList = call != null ? call.getArgumentList() : null;
      if (argList != null) {
        final int actualParamLength = argList.getExpressionCount();
        if ((method.isVarArgs() && actualParamLength + 1 < parametersCount) ||
            (!method.isVarArgs() && actualParamLength < parametersCount)) {
          conflicts.putValue(call, RefactoringBundle.message("refactoring.introduce.parameter.incomplete.call.less.params",
                                                             call.getText(), parametersCount, actualParamLength));
        }
        data.getParameterListToRemove().forEach((IntConsumer)paramNum -> {
          if (paramNum >= actualParamLength) {
            conflicts.putValue(call, RefactoringBundle.message("refactoring.introduce.parameter.incomplete.call.param.not.found",
                                                                   call.getText(), paramNum, actualParamLength));
          }
        });
      }
    }
  }

  @Override
  public boolean processChangeMethodSignature(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    if (!(usage.getElement() instanceof PsiMethod) || !isJavaUsage(usage)) return true;
    PsiMethod method = (PsiMethod)usage.getElement();

    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(data.getParameterName(), method.getBody());
    final MethodJavaDocHelper javaDocHelper = new MethodJavaDocHelper(method);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(data.getProject());

    final PsiClass superClass = data.getMethodToSearchFor().getContainingClass();
    final PsiClass containingClass = method.getContainingClass();
    final PsiSubstitutor substitutor = superClass != null && containingClass != null ? TypeConversionUtil.getSuperClassSubstitutor(superClass, containingClass, PsiSubstitutor.EMPTY)
                                                                                     : PsiSubstitutor.EMPTY;
    PsiParameter parameter = factory.createParameter(data.getParameterName(), substitutor.substitute(data.getForcedType()));
    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, data.isDeclareFinal());

    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    IntList parametersToRemove = data.getParameterListToRemove();
    for (int i = parametersToRemove.size() - 1; i >= 0; i--) {
      int paramNum = parametersToRemove.getInt(i);
      try {
        PsiParameter param = parameters[paramNum];
        PsiDocTag tag = javaDocHelper.getTagForParameter(param);
        if (tag != null) {
          tag.delete();
        }
        param.delete();
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }

    final PsiParameter anchorParameter = getAnchorParameter(method);
    parameter = (PsiParameter)parameterList.addAfter(parameter, anchorParameter);
    JavaCodeStyleManager.getInstance(data.getProject()).shortenClassReferences(parameter);
    final PsiDocTag tagForAnchorParameter = javaDocHelper.getTagForParameter(anchorParameter);
    javaDocHelper.addParameterAfter(data.getParameterName(), tagForAnchorParameter);

    fieldConflictsResolver.fix();

    return false;
  }

  @Nullable
  public static PsiParameter getAnchorParameter(PsiMethod methodToReplaceIn) {
    PsiParameterList parameterList = methodToReplaceIn.getParameterList();
    final PsiParameter anchorParameter;
    final PsiParameter[] parameters = parameterList.getParameters();
    final int length = parameters.length;
    if (!methodToReplaceIn.isVarArgs()) {
      anchorParameter = length > 0 ? parameters[length - 1] : null;
    }
    else {
      LOG.assertTrue(length > 0);
      LOG.assertTrue(parameters[length - 1].isVarArgs());
      anchorParameter = length > 1 ? parameters[length - 2] : null;
    }
    return anchorParameter;
  }

  @Override
  public boolean processAddDefaultConstructor(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) {
    if (!(usage.getElement() instanceof PsiClass) || !isJavaUsage(usage)) return true;
    PsiClass aClass = (PsiClass)usage.getElement();
    if (!(aClass instanceof PsiAnonymousClass)) {
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(data.getProject());
      PsiMethod constructor = factory.createMethodFromText(aClass.getName() + "(){}", aClass);
      constructor = (PsiMethod)CodeStyleManager.getInstance(data.getProject()).reformat(constructor);
      constructor = (PsiMethod)aClass.add(constructor);
      PsiUtil.setModifierProperty(constructor, VisibilityUtil.getVisibilityModifier(aClass.getModifierList()), true);
      processAddSuperCall(data, new UsageInfo(constructor), usages);
    }
    else {
      return true;
    }
    return false;
  }

  @Override
  public boolean processAddSuperCall(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    if (!(usage.getElement() instanceof PsiMethod) || !isJavaUsage(usage)) return true;
    PsiMethod constructor = (PsiMethod)usage.getElement();

    if (!constructor.isConstructor()) return true;

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(data.getProject());
    PsiExpressionStatement superCall = (PsiExpressionStatement)factory.createStatementFromText("super();", constructor);
    superCall = (PsiExpressionStatement)CodeStyleManager.getInstance(data.getProject()).reformat(superCall);
    PsiCodeBlock body = constructor.getBody();
    final PsiStatement[] statements = body.getStatements();
    if (statements.length > 0) {
      superCall = (PsiExpressionStatement)body.addBefore(superCall, statements[0]);
    }
    else {
      superCall = (PsiExpressionStatement)body.add(superCall);
    }
    processChangeMethodUsage(data, new ExternalUsageInfo(((PsiMethodCallExpression)superCall.getExpression()).getMethodExpression()), usages);
    return false;
  }
}
