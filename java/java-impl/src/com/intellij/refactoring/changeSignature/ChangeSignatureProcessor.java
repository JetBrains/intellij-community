/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.refactoring.changeSignature;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.*;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.refactoring.ui.ConflictsDialog;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewDescriptor;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.intellij.util.ObjectUtils.assertNotNull;

/**
 * @author Jeka
 * @since Sep 17, 2001
 */
public class ChangeSignatureProcessor extends ChangeSignatureProcessorBase {
  private static final Logger LOG = Logger.getInstance("#com.intellij.refactoring.changeSignature.ChangeSignatureProcessor");

  public ChangeSignatureProcessor(Project project,
                                  PsiMethod method,
                                  final boolean generateDelegate,
                                  @PsiModifier.ModifierConstant String newVisibility,
                                  String newName,
                                  PsiType newType,
                                  @NotNull ParameterInfoImpl[] parameterInfo) {
    this(project, method, generateDelegate, newVisibility, newName,
         newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
         parameterInfo, null, null, null);
  }

  public ChangeSignatureProcessor(Project project,
                                  PsiMethod method,
                                  final boolean generateDelegate,
                                  @PsiModifier.ModifierConstant String newVisibility,
                                  String newName,
                                  PsiType newType,
                                  ParameterInfoImpl[] parameterInfo,
                                  ThrownExceptionInfo[] exceptionInfos) {
    this(project, method, generateDelegate, newVisibility, newName,
         newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
         parameterInfo, exceptionInfos, null, null);
  }

  public ChangeSignatureProcessor(Project project,
                                  PsiMethod method,
                                  boolean generateDelegate,
                                  @PsiModifier.ModifierConstant String newVisibility,
                                  String newName,
                                  CanonicalTypes.Type newType,
                                  @NotNull ParameterInfoImpl[] parameterInfo,
                                  ThrownExceptionInfo[] thrownExceptions,
                                  Set<PsiMethod> propagateParametersMethods,
                                  Set<PsiMethod> propagateExceptionsMethods) {
    this(project, generateChangeInfo(method, generateDelegate, newVisibility, newName, newType, parameterInfo, thrownExceptions,
                                     propagateParametersMethods, propagateExceptionsMethods));
  }

  public ChangeSignatureProcessor(Project project, final JavaChangeInfo changeInfo) {
    super(project, changeInfo);
    LOG.assertTrue(myChangeInfo.getMethod().isValid());
  }

  private static JavaChangeInfo generateChangeInfo(PsiMethod method,
                                                   boolean generateDelegate,
                                                   @PsiModifier.ModifierConstant String newVisibility,
                                                   String newName,
                                                   CanonicalTypes.Type newType,
                                                   @NotNull ParameterInfoImpl[] parameterInfo,
                                                   ThrownExceptionInfo[] thrownExceptions,
                                                   Set<PsiMethod> propagateParametersMethods,
                                                   Set<PsiMethod> propagateExceptionsMethods) {
    LOG.assertTrue(method.isValid());

    if (propagateParametersMethods == null) {
      propagateParametersMethods = ContainerUtil.newHashSet();
    }

    if (propagateExceptionsMethods == null) {
      propagateExceptionsMethods = ContainerUtil.newHashSet();
    }

    if (newVisibility == null) {
      newVisibility = VisibilityUtil.getVisibilityModifier(method.getModifierList());
    }

    final JavaChangeInfoImpl javaChangeInfo =
      new JavaChangeInfoImpl(newVisibility, method, newName, newType, parameterInfo, thrownExceptions, generateDelegate,
                             propagateParametersMethods, propagateExceptionsMethods);
    javaChangeInfo.setCheckUnusedParameter();
    return javaChangeInfo;
  }

  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(@NotNull UsageInfo[] usages) {
    return new ChangeSignatureViewDescriptor(getChangeInfo().getMethod());
  }

  @Override
  public JavaChangeInfoImpl getChangeInfo() {
    return (JavaChangeInfoImpl)super.getChangeInfo();
  }

  protected void refreshElements(@NotNull PsiElement[] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    getChangeInfo().updateMethod((PsiMethod) elements[0]);
  }

  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    for (ChangeSignatureUsageProcessor processor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
      if (!processor.setupDefaultValues(myChangeInfo, refUsages, myProject)) return false;
    }
    MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<>();
    collectConflictsFromExtensions(refUsages, conflictDescriptions, myChangeInfo);

    final UsageInfo[] usagesIn = refUsages.get();
    RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
    Set<UsageInfo> usagesSet = new HashSet<>(Arrays.asList(usagesIn));
    RenameUtil.removeConflictUsages(usagesSet);
    if (!conflictDescriptions.isEmpty()) {
      if (ApplicationManager.getApplication().isUnitTestMode()) {
        if (ConflictsInTestsException.isTestIgnore()) return true;
        throw new ConflictsInTestsException(conflictDescriptions.values());
      }
      if (myPrepareSuccessfulSwingThreadCallback != null) {
        ConflictsDialog dialog = prepareConflictsDialog(conflictDescriptions, usagesIn);
        if (!dialog.showAndGet()) {
          if (dialog.isShowConflicts()) prepareSuccessful();
          return false;
        }
      }
    }

    if (myChangeInfo.isReturnTypeChanged()) {
      askToRemoveCovariantOverriders(usagesSet);
    }

    refUsages.set(usagesSet.toArray(new UsageInfo[usagesSet.size()]));
    prepareSuccessful();
    return true;
  }

  private void askToRemoveCovariantOverriders(Set<UsageInfo> usages) {
    if (PsiUtil.isLanguageLevel5OrHigher(myChangeInfo.getMethod())) {
      List<UsageInfo> covariantOverriderInfos = new ArrayList<>();
      for (UsageInfo usageInfo : usages) {
        if (usageInfo instanceof OverriderUsageInfo) {
          final OverriderUsageInfo info = (OverriderUsageInfo)usageInfo;
          PsiMethod overrider = assertNotNull(info.getOverridingMethod());
          PsiMethod baseMethod = info.getBaseMethod();
          PsiSubstitutor substitutor = calculateSubstitutor(overrider, baseMethod);
          PsiType type;
          try {
            type = substitutor.substitute(getChangeInfo().newReturnType.getType(myChangeInfo.getMethod(), myManager));
          }
          catch (IncorrectOperationException e) {
            LOG.error(e);
            return;
          }
          final PsiType overriderType = overrider.getReturnType();
          if (overriderType != null && type.isAssignableFrom(overriderType)) {
            covariantOverriderInfos.add(usageInfo);
          }
        }
      }

      // to be able to do filtering
      preprocessCovariantOverriders(covariantOverriderInfos);

      if (!covariantOverriderInfos.isEmpty()) {
        if (ApplicationManager.getApplication().isUnitTestMode() || !isProcessCovariantOverriders()) {
          for (UsageInfo usageInfo : covariantOverriderInfos) {
            usages.remove(usageInfo);
          }
        }
      }
    }
  }

  protected void preprocessCovariantOverriders(final List<UsageInfo> covariantOverriderInfos) {
  }

  protected boolean isProcessCovariantOverriders() {
    String message = RefactoringBundle.message("do.you.want.to.process.overriding.methods.with.covariant.return.type");
    return Messages.showYesNoDialog(myProject, message, ChangeSignatureHandler.REFACTORING_NAME, Messages.getQuestionIcon()) == Messages.YES;
  }

  public static void makeEmptyBody(final PsiElementFactory factory, final PsiMethod delegate) throws IncorrectOperationException {
    PsiCodeBlock body = delegate.getBody();
    if (body != null) {
      body.replace(factory.createCodeBlock());
    }
    else {
      delegate.add(factory.createCodeBlock());
    }
    PsiUtil.setModifierProperty(delegate, PsiModifier.ABSTRACT, false);
  }

  @Nullable
  public static PsiCallExpression addDelegatingCallTemplate(PsiMethod delegate, String newName) throws IncorrectOperationException {
    Project project = delegate.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    PsiCodeBlock body = delegate.getBody();
    assert body != null;
    final PsiCallExpression callExpression;
    if (delegate.isConstructor()) {
      PsiElement callStatement = factory.createStatementFromText("this();", null);
      callStatement = CodeStyleManager.getInstance(project).reformat(callStatement);
      callStatement = body.add(callStatement);
      callExpression = (PsiCallExpression)((PsiExpressionStatement) callStatement).getExpression();
    }
    else {
      if (PsiType.VOID.equals(delegate.getReturnType())) {
        PsiElement callStatement = factory.createStatementFromText(newName + "();", null);
        callStatement = CodeStyleManager.getInstance(project).reformat(callStatement);
        callStatement = body.add(callStatement);
        callExpression = (PsiCallExpression)((PsiExpressionStatement) callStatement).getExpression();
      }
      else {
        PsiElement callStatement = factory.createStatementFromText("return " + newName + "();", null);
        callStatement = CodeStyleManager.getInstance(project).reformat(callStatement);
        callStatement = body.add(callStatement);
        callExpression = (PsiCallExpression)((PsiReturnStatement) callStatement).getReturnValue();
      }
    }
    return callExpression;
  }

  public static PsiSubstitutor calculateSubstitutor(PsiMethod derivedMethod, PsiMethod baseMethod) {
    PsiSubstitutor substitutor;
    if (derivedMethod.getManager().areElementsEquivalent(derivedMethod, baseMethod)) {
      substitutor = PsiSubstitutor.EMPTY;
    }
    else {
      PsiClass baseClass = baseMethod.getContainingClass();
      PsiClass derivedClass = derivedMethod.getContainingClass();
      if (baseClass != null && derivedClass != null && InheritanceUtil.isInheritorOrSelf(derivedClass, baseClass, true)) {
        PsiSubstitutor superClassSubstitutor = TypeConversionUtil.getSuperClassSubstitutor(baseClass, derivedClass, PsiSubstitutor.EMPTY);
        MethodSignature superMethodSignature = baseMethod.getSignature(superClassSubstitutor);
        MethodSignature methodSignature = derivedMethod.getSignature(PsiSubstitutor.EMPTY);
        PsiSubstitutor superMethodSubstitutor = MethodSignatureUtil.getSuperMethodSignatureSubstitutor(methodSignature, superMethodSignature);
        substitutor = superMethodSubstitutor != null ? superMethodSubstitutor : superClassSubstitutor;
      }
      else {
        substitutor = PsiSubstitutor.EMPTY;
      }
    }
    return substitutor;
  }
}
