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
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.changeSignature.*;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectClassDescriptor;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectDelegate;
import com.intellij.refactoring.introduceParameterObject.IntroduceParameterObjectProcessor;
import com.intellij.refactoring.introduceparameterobject.usageInfo.*;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Processor;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JavaIntroduceParameterObjectDelegate
  extends IntroduceParameterObjectDelegate<PsiMethod, ParameterInfoImpl, JavaIntroduceParameterObjectClassDescriptor> {

  @Override
  public ParameterInfoImpl createMergedParameterInfo(Project project,
                                                     JavaIntroduceParameterObjectClassDescriptor descriptor,
                                                     PsiMethod method) {
    final PsiCodeBlock body = method.getBody();
    final String baseParameterName = StringUtil.decapitalize(descriptor.getClassName());

    final String paramName = body != null
                             ? JavaCodeStyleManager.getInstance(project)
                               .suggestUniqueVariableName(baseParameterName, body.getLBrace(), true)
                             : JavaCodeStyleManager.getInstance(project)
                               .propertyNameToVariableName(baseParameterName, VariableKind.PARAMETER);

    final boolean lastVarargsToMerge =
      method.isVarArgs() && descriptor.getParameterInfo(method.getParameterList().getParametersCount() - 1) != null;
    final String classTypeText = descriptor.createFakeClassTypeText();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    return new ParameterInfoImpl(-1, paramName, facade.getElementFactory().createTypeFromText(classTypeText, method), null) {
      @Override
      public PsiExpression getValue(final PsiCallExpression expr) throws IncorrectOperationException {
        final String qualifiedName = StringUtil.getQualifiedName(descriptor.getPackageName(), descriptor.getClassName());
        final PsiClass existingClass = facade.findClass(qualifiedName, expr.getResolveScope());
        if (existingClass == null) return null;
        final String mergedParam = getMergedParam(expr, existingClass, descriptor.getParamsToMerge(), method, lastVarargsToMerge);
        return (PsiExpression)JavaCodeStyleManager.getInstance(project)
          .shortenClassReferences(facade.getElementFactory().createExpressionFromText(mergedParam, expr));
      }
    };
  }

  private static String getMergedParam(PsiCallExpression call,
                                       PsiClass existingClass,
                                       ParameterInfo[] paramsToMerge,
                                       PsiMethod method,
                                       boolean lastVarargsToMerge) {
    final PsiExpression[] args = call.getArgumentList().getExpressions();
    StringBuilder newExpression = new StringBuilder();
    final JavaResolveResult resolvant = call.resolveMethodGenerics();
    final PsiSubstitutor substitutor = resolvant.getSubstitutor();
    newExpression.append("new ")
      .append(JavaPsiFacade.getElementFactory(call.getProject()).createType(existingClass, substitutor).getCanonicalText());
    newExpression.append('(');
    boolean isFirst = true;
    for (ParameterInfo info : paramsToMerge) {
      if (!isFirst) {
        newExpression.append(", ");
      }
      isFirst = false;
      newExpression.append(getArgument(args, info.getOldIndex(), method));
    }
    if (lastVarargsToMerge) {
      final int lastArg = paramsToMerge[paramsToMerge.length - 1].getOldIndex();
      for (int i = lastArg + 1; i < args.length; i++) {
        newExpression.append(',');
        newExpression.append(getArgument(args, i, method));
      }
    }
    newExpression.append(')');
    return newExpression.toString();
  }

  @Nullable
  private static String getArgument(PsiExpression[] args, int i, PsiMethod method) {
    if (i < args.length) {
      return args[i].getText();
    }
    final PsiParameter[] parameters = method.getParameterList().getParameters();
    if (i < parameters.length) return parameters[i].getName();
    return null;
  }

  @Override
  public ChangeInfo createChangeSignatureInfo(PsiMethod method, List<ParameterInfoImpl> infos, boolean delegate) {
    PsiType returnType = method.getReturnType();
    return new JavaChangeInfoImpl(VisibilityUtil.getVisibilityModifier(method.getModifierList()),
                                  method,
                                  method.getName(),
                                  returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null,
                                  infos.toArray(new ParameterInfoImpl[infos.size()]),
                                  null,
                                  delegate,
                                  Collections.emptySet(),
                                  Collections.emptySet());
  }

  @Override
  public <M1 extends PsiNamedElement, P1 extends ParameterInfo> Accessor collectInternalUsages(Collection<FixableUsageInfo> usages,
                                                                                               PsiMethod overridingMethod,
                                                                                               IntroduceParameterObjectClassDescriptor<M1, P1> classDescriptor,
                                                                                               P1 parameterInfo,
                                                                                               String mergedParamName) {
    final LocalSearchScope localSearchScope = new LocalSearchScope(overridingMethod);
    final PsiParameter[] params = overridingMethod.getParameterList().getParameters();
    final PsiParameter parameter = params[parameterInfo.getOldIndex()];
    final String setter = classDescriptor.getSetterName(parameterInfo, overridingMethod);
    final String getter = classDescriptor.getGetterName(parameterInfo, overridingMethod);
    final Accessor[] accessor = new Accessor[]{null};
    ReferencesSearch.search(parameter, localSearchScope).forEach(new Processor<PsiReference>() {
      @Override
      public boolean process(PsiReference reference) {
        final PsiElement refElement = reference.getElement();
        if (refElement instanceof PsiReferenceExpression) {
          final PsiReferenceExpression paramUsage = (PsiReferenceExpression)refElement;
          if (RefactoringUtil.isPlusPlusOrMinusMinus(paramUsage.getParent())) {
            accessor[0] = Accessor.Setter;
            usages.add(new ReplaceParameterIncrementDecrement(paramUsage, mergedParamName, setter, getter));
          }
          else if (RefactoringUtil.isAssignmentLHS(paramUsage)) {
            accessor[0] = Accessor.Setter;
            usages.add(new ReplaceParameterAssignmentWithCall(paramUsage, mergedParamName, setter, getter));
          }
          else {
            if (accessor[0] == null) {
              accessor[0] = Accessor.Getter;
            }
            usages.add(new ReplaceParameterReferenceWithCall(paramUsage, mergedParamName, getter));
          }
        }
        return true;
      }
    });
    return accessor[0];
  }

  @Override
  public void collectAccessibilityUsages(Collection<FixableUsageInfo> usages,
                                         PsiMethod method,
                                         JavaIntroduceParameterObjectClassDescriptor descriptor,
                                         Accessor[] accessors) {
    final ParameterInfoImpl[] parameterInfos = descriptor.getParamsToMerge();
    final PsiClass existingClass = descriptor.getExistingClass();
    final boolean useExisting = descriptor.isGenerateAccessors() || !(descriptor.isUseExistingClass() && existingClass != null);

    final PsiParameter[] psiParameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameterInfos.length; i++) {
      int oldParamIdx = parameterInfos[i].getOldIndex();
      final IntroduceParameterObjectDelegate.Accessor accessor = accessors[i];
      if (accessor != null) {
        final ParameterInfoImpl parameterInfo = parameterInfos[i];
        final PsiParameter parameter = psiParameters[oldParamIdx];
        final PsiField field = descriptor.getField(parameterInfo);
        final String getter = descriptor.getGetter(parameterInfo);
        if (getter == null) {
          usages.add(new AppendAccessorsUsageInfo(parameter, existingClass, useExisting, parameterInfo, true, field));
        }

        if (accessor == IntroduceParameterObjectDelegate.Accessor.Setter && descriptor.getSetter(parameterInfo) == null) {
          usages.add(new AppendAccessorsUsageInfo(parameter, existingClass, useExisting, parameterInfo, false, field));
        }
      }
    }


    final String newVisibility = descriptor.getNewVisibility();
    if (newVisibility != null) {
      usages.add(new BeanClassVisibilityUsageInfo(existingClass, usages.toArray(UsageInfo.EMPTY_ARRAY), newVisibility, descriptor));
    }

    usages.add(new ConstructorJavadocUsageInfo(method, descriptor));

    if (!descriptor.isUseExistingClass()) {
      usages.add(new FixableUsageInfo(method) {
        @Override
        public void fixUsage() throws IncorrectOperationException {
          final PsiClass psiClass = descriptor.getExistingClass();
          for (PsiReference reference : ReferencesSearch.search(method)) {
            final PsiElement place = reference.getElement();
            VisibilityUtil.escalateVisibility(psiClass, place);
            for (PsiMethod constructor : psiClass.getConstructors()) {
              VisibilityUtil.escalateVisibility(constructor, place);
            }
          }
        }
      });
    }
  }

  @Override
  public void collectConflicts(MultiMap<PsiElement, String> conflicts,
                               UsageInfo[] infos,
                               PsiMethod method,
                               JavaIntroduceParameterObjectClassDescriptor classDescriptor) {
    final MoveDestination moveDestination = classDescriptor.getMoveDestination();
    if (moveDestination != null) {
      if (!moveDestination.isTargetAccessible(method.getProject(), method.getContainingFile().getVirtualFile())) {
        conflicts.putValue(method, "Created class won't be accessible");
      }
    }

    if (moveDestination != null) {
      for (UsageInfo info : infos) {
        if (info instanceof IntroduceParameterObjectProcessor.ChangeSignatureUsageWrapper) {
          final UsageInfo usageInfo = ((IntroduceParameterObjectProcessor.ChangeSignatureUsageWrapper)info).getInfo();
          if (usageInfo instanceof OverriderMethodUsageInfo) {
            final PsiElement overridingMethod = ((OverriderMethodUsageInfo)usageInfo).getOverridingMethod();

            if (!moveDestination.isTargetAccessible(overridingMethod.getProject(), overridingMethod.getContainingFile().getVirtualFile())) {
              conflicts.putValue(overridingMethod, "Created class won't be accessible");
            }
          }
        }
      }
    }
  }
}
