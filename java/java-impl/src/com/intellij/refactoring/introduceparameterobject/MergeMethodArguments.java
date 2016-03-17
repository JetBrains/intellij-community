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
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.refactoring.changeSignature.ChangeInfo;
import com.intellij.refactoring.changeSignature.JavaChangeInfoImpl;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MergeMethodArguments  {
  private final PsiMethod method;
  private final PsiClass myContainingClass;
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
                              final boolean keepMethodAsDelegate,
                              final PsiClass containingClass) {
    this.paramsToMerge = paramsToMerge;
    this.packageName = packageName;
    this.className = className;
    this.parameterName = parameterName;
    this.method = method;
    myContainingClass = containingClass;
    lastParamIsVararg = method.isVarArgs() && isParameterToMerge(method.getParameterList().getParametersCount() - 1);
    myKeepMethodAsDelegate = keepMethodAsDelegate;
    this.typeParams = new ArrayList<PsiTypeParameter>(typeParams);
  }

  public ChangeInfo createChangeInfo() {
    final Project project = method.getProject();
    final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    String packageName;
    if (myContainingClass != null) {
      packageName = myContainingClass.getQualifiedName();
      if (packageName == null) {
        packageName = myContainingClass.getName();
      }
    }
    else {
      packageName = this.packageName;
    }

    String text = StringUtil.getQualifiedName(packageName, className);
    if (!typeParams.isEmpty()) {
      text += "<" + StringUtil.join(typeParams, new Function<PsiTypeParameter, String>() {
        @Override
        public String fun(PsiTypeParameter parameter) {
          return parameter.getName();
        }
      }, ", ") + ">";
    }
    final PsiType classType = factory.createTypeFromText(text, method);
    final List<ParameterInfoImpl> parametersInfo = new ArrayList<ParameterInfoImpl>();

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
    PsiType returnType = method.getReturnType();
    return new JavaChangeInfoImpl(VisibilityUtil.getVisibilityModifier(method.getModifierList()),
                                  method,
                                  method.getName(),
                                  returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null,
                                  parametersInfo.toArray(new ParameterInfoImpl[parametersInfo.size()]),
                                  null,
                                  myKeepMethodAsDelegate,
                                  Collections.emptySet(),
                                  Collections.emptySet());
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
