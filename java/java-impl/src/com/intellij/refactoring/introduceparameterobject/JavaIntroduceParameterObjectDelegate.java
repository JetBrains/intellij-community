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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.MoveDestination;
import com.intellij.refactoring.RefactoringActionHandler;
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
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class JavaIntroduceParameterObjectDelegate
  extends IntroduceParameterObjectDelegate<PsiMethod, ParameterInfoImpl, JavaIntroduceParameterObjectClassDescriptor> {

  @Override
  public  List<ParameterInfoImpl> getAllMethodParameters(PsiMethod sourceMethod) {
    return new JavaMethodDescriptor(sourceMethod).getParameters();
  }

  @Override
  public boolean isEnabledOn(PsiElement element) {
    return PsiTreeUtil.getParentOfType(element, PsiMethod.class, false) != null;
  }

  @Override
  public RefactoringActionHandler getHandler(PsiElement element) {
    return new IntroduceParameterObjectHandler();
  }

  @Override
  public ParameterInfoImpl createMergedParameterInfo(JavaIntroduceParameterObjectClassDescriptor descriptor,
                                                     PsiMethod method,
                                                     List<ParameterInfoImpl> oldMethodParameters) {
    final PsiCodeBlock body = method.getBody();
    String baseParameterName = StringUtil.decapitalize(descriptor.getClassName());
    final Project project = method.getProject();

    if (!PsiNameHelper.getInstance(project).isIdentifier(baseParameterName, LanguageLevel.HIGHEST)) {
      baseParameterName = StringUtil.fixVariableNameDerivedFromPropertyName(baseParameterName);
    }
    final String paramName = body != null
                             ? JavaCodeStyleManager.getInstance(project)
                               .suggestUniqueVariableName(baseParameterName, body.getLBrace(), true)
                             : JavaCodeStyleManager.getInstance(project)
                               .propertyNameToVariableName(baseParameterName, VariableKind.PARAMETER);

    final String classTypeText = descriptor.createFakeClassTypeText();
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    return new ParameterInfoImpl(-1, paramName, facade.getElementFactory().createTypeFromText(classTypeText, method), null) {
      @Nullable
      @Override
      public PsiElement getActualValue(PsiElement exp) {
        final IntroduceParameterObjectDelegate<PsiNamedElement, ParameterInfo, IntroduceParameterObjectClassDescriptor<PsiNamedElement, ParameterInfo>> delegate = findDelegate(exp);
        return delegate != null ? delegate.createNewParameterInitializerAtCallSite(exp, descriptor, oldMethodParameters) : null;
      }
    };
  }

  @Override
  public PsiElement createNewParameterInitializerAtCallSite(PsiElement callExpression,
                                                            IntroduceParameterObjectClassDescriptor descriptor,
                                                            List<? extends ParameterInfo> oldMethodParameters) {
    if (callExpression instanceof PsiCallExpression) {
      final PsiCallExpression expr = (PsiCallExpression)callExpression;
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(expr.getProject());
      final String qualifiedName = StringUtil.getQualifiedName(descriptor.getPackageName(), descriptor.getClassName());
      final PsiClass existingClass = facade.findClass(qualifiedName, expr.getResolveScope());
      if (existingClass == null) return null;

      final PsiExpressionList argumentList = expr.getArgumentList();
      if (argumentList == null) return null;

      final PsiExpression[] args = argumentList.getExpressions();
      StringBuilder newExpression = new StringBuilder();
      final JavaResolveResult resolvant = expr.resolveMethodGenerics();
      final PsiSubstitutor substitutor = resolvant.getSubstitutor();
      newExpression.append("new ")
        .append(JavaPsiFacade.getElementFactory(expr.getProject()).createType(existingClass, substitutor).getCanonicalText());
      newExpression.append('(');
      newExpression.append(getMergedArgs(descriptor, oldMethodParameters, args));
      newExpression.append(')');

      return JavaCodeStyleManager.getInstance(callExpression.getProject())
        .shortenClassReferences(facade.getElementFactory().createExpressionFromText(newExpression.toString(), expr));
    }
    return null;
  }

  public static String getMergedArgs(IntroduceParameterObjectClassDescriptor descriptor,
                                     final List<? extends ParameterInfo> oldMethodParameters,
                                     final PsiElement[] args) {
    final StringBuilder newExpression = new StringBuilder();
    final ParameterInfo[] paramsToMerge = descriptor.getParamsToMerge();
    newExpression.append(StringUtil.join(paramsToMerge, parameterInfo -> getArgument(args, parameterInfo.getOldIndex(), oldMethodParameters), ", "));
    final ParameterInfo lastParam = paramsToMerge[paramsToMerge.length - 1];
    if (lastParam instanceof JavaParameterInfo && ((JavaParameterInfo)lastParam).isVarargType()) {
      final int lastArg = lastParam.getOldIndex();
      for (int i = lastArg + 1; i < args.length; i++) {
        newExpression.append(',');
        newExpression.append(getArgument(args, i, oldMethodParameters));
      }
    }

    return newExpression.toString();
  }

  @Nullable
  private static String getArgument(PsiElement[] args, int i, List<? extends ParameterInfo> oldParameters) {
    if (i < args.length) {
      return args[i].getText();
    }
    return i < oldParameters.size() ? oldParameters.get(i).getName() : null;
  }

  @Override
  public ChangeInfo createChangeSignatureInfo(PsiMethod method, List<ParameterInfoImpl> newParameterInfos, boolean delegate) {
    PsiType returnType = method.getReturnType();
    return new JavaChangeInfoImpl(VisibilityUtil.getVisibilityModifier(method.getModifierList()),
                                  method,
                                  method.getName(),
                                  returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null,
                                  newParameterInfos.toArray(new ParameterInfoImpl[newParameterInfos.size()]),
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
    ReferencesSearch.search(parameter, localSearchScope).forEach(reference -> {
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
    );
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

      final PsiFile containingFile = method.getContainingFile();
      final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
      PsiDirectory directory = moveDestination.getTargetDirectory(containingDirectory);
      if (directory != null) {
        PsiFile file = directory.findFile(classDescriptor.getClassName() + ".java");
        if (file != null) {
          VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
          if (virtualFile != null) {
            conflicts.putValue(method, "File already exits: " + virtualFile.getPresentableUrl());
          }
        }
      }
    }

    if (moveDestination != null) {
      boolean constructorMiss = false;
      for (UsageInfo info : infos) {
        if (info instanceof IntroduceParameterObjectProcessor.ChangeSignatureUsageWrapper) {
          final UsageInfo usageInfo = ((IntroduceParameterObjectProcessor.ChangeSignatureUsageWrapper)info).getInfo();
          if (usageInfo instanceof OverriderMethodUsageInfo) {
            final PsiElement overridingMethod = ((OverriderMethodUsageInfo)usageInfo).getOverridingMethod();

            if (!moveDestination.isTargetAccessible(overridingMethod.getProject(), overridingMethod.getContainingFile().getVirtualFile())) {
              conflicts.putValue(overridingMethod, "Created class won't be accessible");
            }
          }
          if (!constructorMiss && classDescriptor.isUseExistingClass() && usageInfo instanceof MethodCallUsageInfo && classDescriptor.getExistingClassCompatibleConstructor() == null) {
            conflicts.putValue(classDescriptor.getExistingClass(), "Existing class misses compatible constructor");
            constructorMiss = true;
          }
        }
      }
    }

    if (classDescriptor.isUseExistingClass()) {
      for (ParameterInfoImpl info : classDescriptor.getParamsToMerge()) {
        Object existingClassBean = classDescriptor.getBean(info);
        if (existingClassBean == null) {
          conflicts.putValue(classDescriptor.getExistingClass(), "No field associated with " + info.getName() + " found");
        }
      }
    }
  }
}
