/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceParameter;

import com.intellij.codeInsight.ChangeContextUtil;
import com.intellij.lang.Language;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.ExpressionConverter;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.psi.impl.source.resolve.DefaultParameterTypeInferencePolicy;
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
import gnu.trove.TIntArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Maxim.Medvedev
 */
public class JavaIntroduceParameterMethodUsagesProcessor implements IntroduceParameterMethodUsagesProcessor {
  private static final Logger LOG =
    Logger.getInstance("#com.intellij.refactoring.introduceParameter.JavaIntroduceParameterMethodUsagesProcessor");
  private static final JavaLanguage myLanguage = Language.findInstance(JavaLanguage.class);

  private static boolean isJavaUsage(UsageInfo usage) {
    PsiElement e = usage.getElement();
    return e != null && e.getLanguage().is(myLanguage);
  }

  public boolean isMethodUsage(UsageInfo usage) {
    return RefactoringUtil.isMethodUsage(usage.getElement()) && isJavaUsage(usage);
  }

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
    if (argList == null) return true;
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
        ExpressionConverter.getExpression(data.getParameterInitializer().getExpression(), StdLanguages.JAVA, data.getProject());
      assert initializer instanceof PsiExpression;
      if (initializer instanceof PsiNewExpression) {
        if (!PsiDiamondTypeUtil.canChangeContextForDiamond((PsiNewExpression)initializer, ((PsiNewExpression)initializer).getType())) {
          initializer = PsiDiamondTypeUtil.expandTopLevelDiamondsInside((PsiNewExpression)initializer);
        }
      }
      substituteTypeParametersInInitializer(initializer, callExpression, argList, methodToSearchFor);
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
    removeParametersFromCall(argumentList, data.getParametersToRemove(), methodToSearchFor);
    return false;
  }

  private static void substituteTypeParametersInInitializer(PsiElement initializer,
                                                            PsiCall callExpression,
                                                            PsiExpressionList argList,
                                                            PsiMethod method) {
    final Project project = method.getProject();
    final PsiSubstitutor psiSubstitutor = JavaPsiFacade.getInstance(project).getResolveHelper()
      .inferTypeArguments(method.getTypeParameters(), method.getParameterList().getParameters(),
                          argList.getExpressions(), PsiSubstitutor.EMPTY, callExpression,
                          DefaultParameterTypeInferencePolicy.INSTANCE);
    RefactoringUtil.replaceMovedMemberTypeParameters(initializer, PsiUtil.typeParametersIterable(method), psiSubstitutor,
                                                     JavaPsiFacade.getElementFactory(project));
  }

  private static void removeParametersFromCall(@NotNull final PsiExpressionList argList, TIntArrayList parametersToRemove, PsiMethod method) {
    final int parametersCount = method.getParameterList().getParametersCount();
    final PsiExpression[] exprs = argList.getExpressions();
    parametersToRemove.forEachDescending(paramNum -> {
      try {
        //parameter was introduced before varargs
        if (method.isVarArgs() && paramNum == parametersCount - 1) {
          for (int i = paramNum + 1; i < exprs.length; i++) {
            exprs[i].delete();
          }
        }
        else if (paramNum < exprs.length) {
          exprs[paramNum].delete();
        }
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
      return true;
    });
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


  public void findConflicts(IntroduceParameterData data, UsageInfo[] usages, final MultiMap<PsiElement, String> conflicts) {
    final PsiMethod method = data.getMethodToReplaceIn();
    final int parametersCount = method.getParameterList().getParametersCount();
    for (UsageInfo usage : usages) {
      final PsiElement element = usage.getElement();
      if (element instanceof PsiMethodReferenceExpression && !ApplicationManager.getApplication().isUnitTestMode()) {
        conflicts.putValue(element, RefactoringBundle.message("expand.method.reference.warning"));
      }
      if (!isMethodUsage(usage)) continue;
      final PsiCall call = RefactoringUtil.getCallExpressionByMethodReference(element);
      final PsiExpressionList argList = call.getArgumentList();
      if (argList != null) {
        final int actualParamLength = argList.getExpressionCount();
        if ((method.isVarArgs() && actualParamLength + 1 < parametersCount) ||
            (!method.isVarArgs() && actualParamLength < parametersCount)) {
          conflicts.putValue(call, "Incomplete call(" + call.getText() +"): " + parametersCount + " parameters expected but only " + actualParamLength + " found");
        }
        data.getParametersToRemove().forEach(paramNum -> {
          if (paramNum >= actualParamLength) {
            conflicts.putValue(call, "Incomplete call(" + call.getText() +"): expected to delete the " + paramNum + " parameter but only " + actualParamLength + " parameters found");
          }
          return true;
        });
      }
    }
  }

  public boolean processChangeMethodSignature(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    if (!(usage.getElement() instanceof PsiMethod) || !isJavaUsage(usage)) return true;
    PsiMethod method = (PsiMethod)usage.getElement();

    final FieldConflictsResolver fieldConflictsResolver = new FieldConflictsResolver(data.getParameterName(), method.getBody());
    final MethodJavaDocHelper javaDocHelper = new MethodJavaDocHelper(method);
    PsiElementFactory factory = JavaPsiFacade.getInstance(data.getProject()).getElementFactory();

    final PsiClass superClass = data.getMethodToSearchFor().getContainingClass();
    final PsiClass containingClass = method.getContainingClass();
    final PsiSubstitutor substitutor = superClass != null && containingClass != null ? TypeConversionUtil.getSuperClassSubstitutor(superClass, containingClass, PsiSubstitutor.EMPTY)
                                                                                     : PsiSubstitutor.EMPTY;
    PsiParameter parameter = factory.createParameter(data.getParameterName(), substitutor.substitute(data.getForcedType()));
    PsiUtil.setModifierProperty(parameter, PsiModifier.FINAL, data.isDeclareFinal());

    final PsiParameterList parameterList = method.getParameterList();
    final PsiParameter[] parameters = parameterList.getParameters();
    data.getParametersToRemove().forEachDescending(paramNum -> {
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
      return true;
    });

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

  public boolean processAddDefaultConstructor(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) {
    if (!(usage.getElement() instanceof PsiClass) || !isJavaUsage(usage)) return true;
    PsiClass aClass = (PsiClass)usage.getElement();
    if (!(aClass instanceof PsiAnonymousClass)) {
      final PsiElementFactory factory = JavaPsiFacade.getInstance(data.getProject()).getElementFactory();
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

  public boolean processAddSuperCall(IntroduceParameterData data, UsageInfo usage, UsageInfo[] usages) throws IncorrectOperationException {
    if (!(usage.getElement() instanceof PsiMethod) || !isJavaUsage(usage)) return true;
    PsiMethod constructor = (PsiMethod)usage.getElement();

    if (!constructor.isConstructor()) return true;

    final PsiElementFactory factory = JavaPsiFacade.getInstance(data.getProject()).getElementFactory();
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
