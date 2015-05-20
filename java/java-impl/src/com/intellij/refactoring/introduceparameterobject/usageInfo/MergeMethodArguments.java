/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.introduceparameterobject.usageInfo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.source.PsiImmediateClassType;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"MethodWithTooManyParameters"})
public class MergeMethodArguments extends FixableUsageInfo {
  private final PsiMethod method;
  private final PsiClass myContainingClass;
  private final boolean myChangeSignature;
  private final boolean myKeepMethodAsDelegate;
  private final List<PsiTypeParameter> typeParams;
  private final String className;
  private final String packageName;
  private final String parameterName;
  private final int[] paramsToMerge;
  private final boolean lastParamIsVararg;

  public MergeMethodArguments(PsiMethod method,
                              String className,
                              String packageName,
                              String parameterName,
                              int[] paramsToMerge,
                              List<PsiTypeParameter> typeParams,
                              final boolean keepMethodAsDelegate, final PsiClass containingClass, boolean changeSignature) {
    super(method);
    this.paramsToMerge = paramsToMerge;
    this.packageName = packageName;
    this.className = className;
    this.parameterName = parameterName;
    this.method = method;
    myContainingClass = containingClass;
    myChangeSignature = changeSignature;
    lastParamIsVararg = method.isVarArgs() && isParameterToMerge(method.getParameterList().getParametersCount() - 1);
    myKeepMethodAsDelegate = keepMethodAsDelegate;
    this.typeParams = new ArrayList<PsiTypeParameter>(typeParams);
  }

  public void fixUsage() throws IncorrectOperationException {
    final Project project = method.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    final PsiMethod deepestSuperMethod = method.findDeepestSuperMethod();
    final PsiClass psiClass;
    if (myContainingClass != null) {
      psiClass = myContainingClass.findInnerClassByName(className, false);
    }
    else {
      psiClass = psiFacade.findClass(StringUtil.getQualifiedName(packageName, className), GlobalSearchScope.allScope(project));
    }
    assert psiClass != null;
    PsiSubstitutor subst = PsiSubstitutor.EMPTY;
    if (deepestSuperMethod != null) {
      final PsiClass parentClass = deepestSuperMethod.getContainingClass();
      final PsiSubstitutor parentSubstitutor =
        TypeConversionUtil.getSuperClassSubstitutor(parentClass, method.getContainingClass(), PsiSubstitutor.EMPTY);
      for (int i1 = 0; i1 < psiClass.getTypeParameters().length; i1++) {
        final PsiTypeParameter typeParameter = psiClass.getTypeParameters()[i1];
        for (PsiTypeParameter parameter : parentClass.getTypeParameters()) {
          if (Comparing.strEqual(typeParameter.getName(), parameter.getName())) {
            subst = subst.put(typeParameter, parentSubstitutor.substitute(
              new PsiImmediateClassType(parameter, PsiSubstitutor.EMPTY)));
            break;
          }
        }
      }
    }
    final List<ParameterInfoImpl> parametersInfo = new ArrayList<ParameterInfoImpl>();
    final PsiClassType classType = JavaPsiFacade.getElementFactory(project).createType(psiClass, subst);

    final ParameterInfoImpl mergedParamInfo = new ParameterInfoImpl(-1, parameterName, classType, null) {
      @Override
      public PsiExpression getValue(final PsiCallExpression expr) throws IncorrectOperationException {
        return (PsiExpression)JavaCodeStyleManager.getInstance(project)
          .shortenClassReferences(psiFacade.getElementFactory().createExpressionFromText(getMergedParam(expr), expr));
      }
    };

    int firstIncludedIdx = -1;
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameters.length; i++) {
      if (!isParameterToMerge(i)) {
        parametersInfo.add(new ParameterInfoImpl(i, parameters[i].getName(), parameters[i].getType()));
      } else if (firstIncludedIdx == -1) {
        firstIncludedIdx = i;
      }
    }

    parametersInfo.add(firstIncludedIdx == -1 ? 0 : firstIncludedIdx, mergedParamInfo);
    final SmartPsiElementPointer<PsiMethod> meth = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(method);

    final Runnable performChangeSignatureRunnable = new Runnable() {
      @Override
      public void run() {
        final PsiMethod psiMethod = meth.getElement();
        if (psiMethod == null) return;
        if (myChangeSignature) {
          final ChangeSignatureProcessor changeSignatureProcessor =
            new ChangeSignatureProcessor(psiMethod.getProject(), psiMethod,
                                         myKeepMethodAsDelegate, null, psiMethod.getName(),
                                         psiMethod.getReturnType(),
                                         parametersInfo.toArray(new ParameterInfoImpl[parametersInfo.size()]));
          changeSignatureProcessor.run();
        }
      }
    };
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      performChangeSignatureRunnable.run();
    } else {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          CommandProcessor.getInstance().runUndoTransparentAction(performChangeSignatureRunnable);
        }
      });
    }
  }

  private boolean isParameterToMerge(int index) {
    for (int i : paramsToMerge) {
      if (i == index) {
        return true;
      }
    }
    return false;
  }

  private String getMergedParam(PsiCallExpression call) {
    final PsiExpression[] args = call.getArgumentList().getExpressions();
    StringBuffer newExpression = new StringBuffer();
    final String qualifiedName;
    if (myContainingClass != null) {
      final String containingClassQName = myContainingClass.getQualifiedName();
      if (containingClassQName != null) {
        qualifiedName = containingClassQName + "." + className;
      } else {
        qualifiedName = className;
      }
    }
    else {
      qualifiedName = StringUtil.getQualifiedName(packageName, className);
    }
    newExpression.append("new ").append(qualifiedName);
    if (!typeParams.isEmpty()) {
      final JavaResolveResult resolvant = call.resolveMethodGenerics();
      final PsiSubstitutor substitutor = resolvant.getSubstitutor();
      newExpression.append('<');
      final Map<PsiTypeParameter, PsiType> substitutionMap = substitutor.getSubstitutionMap();
      newExpression.append(StringUtil.join(typeParams, new Function<PsiTypeParameter, String>() {
        public String fun(final PsiTypeParameter typeParameter) {
          final PsiType boundType = substitutionMap.get(typeParameter);
          if (boundType != null) {
            return boundType.getCanonicalText();
          }
          else {
            return typeParameter.getName();
          }
        }
      }, ", "));
      newExpression.append('>');
    }
    newExpression.append('(');
    boolean isFirst = true;
    for (int index : paramsToMerge) {
      if (!isFirst) {
        newExpression.append(", ");
      }
      isFirst = false;
      newExpression.append(getArgument(args, index));
    }
    if (lastParamIsVararg) {
      final int lastArg = paramsToMerge[paramsToMerge.length - 1];
      for (int i = lastArg + 1; i < args.length; i++) {
        newExpression.append(',');
        newExpression.append(getArgument(args, i));
      }
    }
    newExpression.append(')');
    return newExpression.toString();
  }

  @Nullable
  private String getArgument(PsiExpression[] args, int i) {
    if (i < args.length) {
      return args[i].getText();
    }
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (i < parameters.length) return parameters[i].getName();
    return null;
  }
}
