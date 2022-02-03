// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.introduceparameterobject;

import com.intellij.codeInsight.highlighting.ReadWriteAccessDetector;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
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
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.intellij.refactoring.changeSignature.ParameterInfo.NEW_PARAMETER;

public class JavaIntroduceParameterObjectDelegate
  extends IntroduceParameterObjectDelegate<PsiMethod, ParameterInfoImpl, JavaIntroduceParameterObjectClassDescriptor> {

  @Override
  public  List<ParameterInfoImpl> getAllMethodParameters(@NotNull PsiMethod sourceMethod) {
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
                                                     List<? extends ParameterInfoImpl> oldMethodParameters) {
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
    return new ParameterInfoImpl(NEW_PARAMETER, paramName, facade.getElementFactory().createTypeFromText(classTypeText, method), null) {
      @Nullable
      @Override
      public PsiElement getActualValue(PsiElement exp, Object substitutor) {
        final IntroduceParameterObjectDelegate<PsiNamedElement, ParameterInfo, IntroduceParameterObjectClassDescriptor<PsiNamedElement, ParameterInfo>> delegate = findDelegate(exp);
        return delegate != null ? delegate.createNewParameterInitializerAtCallSite(exp, descriptor, oldMethodParameters, substitutor) : null;
      }
    };
  }

  @Override
  public PsiElement createNewParameterInitializerAtCallSite(PsiElement callExpression,
                                                            IntroduceParameterObjectClassDescriptor descriptor,
                                                            List<? extends ParameterInfo> oldMethodParameters,
                                                            Object substitutor) {
    if (callExpression instanceof PsiCallExpression) {
      final PsiCallExpression expr = (PsiCallExpression)callExpression;
      final JavaPsiFacade facade = JavaPsiFacade.getInstance(expr.getProject());
      final String qualifiedName = StringUtil.getQualifiedName(descriptor.getPackageName(), descriptor.getClassName());
      final PsiClass existingClass = facade.findClass(qualifiedName, expr.getResolveScope());
      if (existingClass == null) return null;

      final PsiExpressionList argumentList = expr.getArgumentList();
      if (argumentList == null) return null;

      final PsiExpression[] args = argumentList.getExpressions();
      @NonNls StringBuilder newExpression = new StringBuilder();
      newExpression.append("new ").append(existingClass.getQualifiedName());
      if (descriptor instanceof JavaIntroduceParameterObjectClassDescriptor) {
        List<String> types = new ArrayList<>();
        for (PsiTypeParameter parameter : ((JavaIntroduceParameterObjectClassDescriptor)descriptor).getTypeParameters()) {
          PsiType type = ((PsiSubstitutor)substitutor).substitute(parameter);
          if (type == null) {
            types.clear();
            break;
          }
          types.add(type.getCanonicalText());
        }
        if (!types.isEmpty()) {
          newExpression.append("<").append(StringUtil.join(types, ", ")).append(">");
        }
      }
      newExpression.append('(');
      newExpression.append(getMergedArgs(descriptor, oldMethodParameters, args));
      newExpression.append(')');

      PsiNewExpression newClassExpression = (PsiNewExpression)JavaCodeStyleManager.getInstance(callExpression.getProject())
        .shortenClassReferences(facade.getElementFactory().createExpressionFromText(newExpression.toString(), expr));
      if (PsiDiamondTypeUtil.canChangeContextForDiamond(newClassExpression, newClassExpression.getType())) {
        RemoveRedundantTypeArgumentsUtil.replaceExplicitWithDiamond(newClassExpression.getClassOrAnonymousClassReference().getParameterList());
      }
      return newClassExpression;
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
  public ChangeInfo createChangeSignatureInfo(PsiMethod method, List<? extends ParameterInfoImpl> newParameterInfos, boolean delegate) {
    PsiType returnType = method.getReturnType();
    return new JavaChangeInfoImpl(VisibilityUtil.getVisibilityModifier(method.getModifierList()),
                                  method,
                                  method.getName(),
                                  returnType != null ? CanonicalTypes.createTypeWrapper(returnType) : null,
                                  newParameterInfos.toArray(new ParameterInfoImpl[0]),
                                  null,
                                  delegate,
                                  Collections.emptySet(),
                                  Collections.emptySet());
  }

  @Override
  public <M1 extends PsiNamedElement, P1 extends ParameterInfo> ReadWriteAccessDetector.Access collectInternalUsages(Collection<? super FixableUsageInfo> usages,
                                                                                                                     PsiMethod overridingMethod,
                                                                                                                     IntroduceParameterObjectClassDescriptor<M1, P1> classDescriptor,
                                                                                                                     P1 parameterInfo,
                                                                                                                     String mergedParamName) {
    final LocalSearchScope localSearchScope = new LocalSearchScope(overridingMethod);
    final PsiParameter[] params = overridingMethod.getParameterList().getParameters();
    final PsiParameter parameter = params[parameterInfo.getOldIndex()];
    final ReadWriteAccessDetector detector = ReadWriteAccessDetector.findDetector(parameter);
    assert detector != null;
    
    final List<PsiReferenceExpression> readUsages = new ArrayList<>();
    final List<PsiReferenceExpression> readWriteUsages = new ArrayList<>();
    final List<PsiReferenceExpression> writeUsages = new ArrayList<>();
    
    ReferencesSearch.search(parameter, localSearchScope).forEach(reference -> {
         final PsiElement refElement = reference.getElement();
         if (refElement instanceof PsiReferenceExpression) {
           final PsiReferenceExpression paramUsage = (PsiReferenceExpression)refElement;
           switch (detector.getExpressionAccess(refElement)) {
             case Read:
               readUsages.add(paramUsage);
               break;
             case ReadWrite:
                 readWriteUsages.add(paramUsage);
                 break;
             case Write:
                 writeUsages.add(paramUsage);
                 break;
           }
         }
         return true;
       }
    );

    ReadWriteAccessDetector.Access access =
      readWriteUsages.isEmpty() && writeUsages.isEmpty() ? ReadWriteAccessDetector.Access.Read : ReadWriteAccessDetector.Access.Write;

    if (access == ReadWriteAccessDetector.Access.Read && readUsages.isEmpty()) {
      return null;
    }

    final String setter = classDescriptor.getSetterName(parameterInfo, overridingMethod);
    final String getter = classDescriptor.getGetterName(parameterInfo, overridingMethod, access);
    
    readUsages.stream()
      .map(paramUsage -> new ReplaceParameterReferenceWithCall(paramUsage, mergedParamName, getter)).forEach(usages::add);
    readWriteUsages.stream()
      .map(paramUsage -> new ReplaceParameterIncrementDecrement(paramUsage, mergedParamName, setter, getter))
      .forEach(usages::add);
    writeUsages.stream()
      .map(paramUsage -> new ReplaceParameterAssignmentWithCall(paramUsage, mergedParamName, setter, getter))
      .forEach(usages::add);

    return access;
  }

  @Override
  public void collectUsagesToGenerateMissedFieldAccessors(Collection<? super FixableUsageInfo> usages,
                                                          PsiMethod method,
                                                          JavaIntroduceParameterObjectClassDescriptor descriptor,
                                                          ReadWriteAccessDetector.Access[] accessors) {
    final ParameterInfoImpl[] parameterInfos = descriptor.getParamsToMerge();
    final PsiClass existingClass = descriptor.getExistingClass();
    final boolean useExisting = descriptor.isGenerateAccessors() || !(descriptor.isUseExistingClass() && existingClass != null);

    final PsiParameter[] psiParameters = method.getParameterList().getParameters();
    for (int i = 0; i < parameterInfos.length; i++) {
      int oldParamIdx = parameterInfos[i].getOldIndex();
      final ReadWriteAccessDetector.Access accessor = accessors[i];
      if (accessor != null) {
        final ParameterInfoImpl parameterInfo = parameterInfos[i];
        final PsiParameter parameter = psiParameters[oldParamIdx];
        final PsiField field = descriptor.getField(parameterInfo);
        final String getter = descriptor.getGetter(parameterInfo);
        if (getter == null) {
          usages.add(new AppendAccessorsUsageInfo(parameter, existingClass, useExisting, parameterInfo, true, field));
        }

        if (accessor == ReadWriteAccessDetector.Access.Write && descriptor.getSetter(parameterInfo) == null) {
          usages.add(new AppendAccessorsUsageInfo(parameter, existingClass, useExisting, parameterInfo, false, field));
        }
      }
    }
  }

  @Override
  public void collectAdditionalFixes(Collection<? super FixableUsageInfo> usages,
                                     final PsiMethod method,
                                     final JavaIntroduceParameterObjectClassDescriptor descriptor) {

    if (method.getDocComment() != null) {
      usages.add(new ConstructorJavadocUsageInfo(method, descriptor));
    }

    final String newVisibility = descriptor.getNewVisibility();
    if (newVisibility != null) {
      usages.add(new BeanClassVisibilityUsageInfo(descriptor.getExistingClass(), usages.toArray(UsageInfo.EMPTY_ARRAY), newVisibility, descriptor));
    }

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
        conflicts.putValue(method, JavaRefactoringBundle.message("introduce.parameter.object.error.created.class.wont.be.accessible"));
      }

      if (!classDescriptor.isCreateInnerClass() && !classDescriptor.isUseExistingClass()) {
        final PsiFile containingFile = method.getContainingFile();
        final PsiDirectory containingDirectory = containingFile.getContainingDirectory();
        PsiDirectory directory = moveDestination.getTargetDirectory(containingDirectory);
        if (directory != null) {
          PsiFile file = directory.findFile(classDescriptor.getClassName() + ".java");
          if (file != null) {
            VirtualFile virtualFile = PsiUtilCore.getVirtualFile(file);
            if (virtualFile != null) {
              conflicts.putValue(method, JavaRefactoringBundle.message("introduce.parameter.object.error.file.already.exits", virtualFile.getPresentableUrl()));
            }
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
            final PsiElement overridingMethod = ((OverriderMethodUsageInfo<?>)usageInfo).getOverridingMethod();

            if (!moveDestination.isTargetAccessible(overridingMethod.getProject(), overridingMethod.getContainingFile().getVirtualFile())) {
              conflicts.putValue(overridingMethod,
                                 JavaRefactoringBundle.message("introduce.parameter.object.error.created.class.wont.be.accessible"));
            }
          }
          if (!constructorMiss && classDescriptor.isUseExistingClass() && usageInfo instanceof MethodCallUsageInfo && classDescriptor.getExistingClassCompatibleConstructor() == null) {
            conflicts.putValue(classDescriptor.getExistingClass(), JavaRefactoringBundle
              .message("introduce.parameter.object.error.existing.class.misses.compatible.constructor"));
            constructorMiss = true;
          }
        }
      }
    }

    if (classDescriptor.isUseExistingClass()) {
      for (ParameterInfoImpl info : classDescriptor.getParamsToMerge()) {
        Object existingClassBean = classDescriptor.getBean(info);
        if (existingClassBean == null) {
          conflicts.putValue(classDescriptor.getExistingClass(),
                             JavaRefactoringBundle.message("introduce.parameter.object.error.no.field.associated.found", info.getName()));
        }
      }
    }
  }
}
