// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeSignature;

import com.intellij.java.refactoring.JavaRefactoringBundle;
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
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Jeka
 */
public class ChangeSignatureProcessor extends ChangeSignatureProcessorBase {
  private static final Logger LOG = Logger.getInstance(ChangeSignatureProcessor.class);

  public ChangeSignatureProcessor(Project project,
                                  PsiMethod method,
                                  final boolean generateDelegate,
                                  @Nullable // null means unchanged
                                  @PsiModifier.ModifierConstant String newVisibility,
                                  String newName,
                                  PsiType newType,
                                  ParameterInfoImpl @NotNull [] parameterInfo) {
    this(project, method, generateDelegate, newVisibility, newName,
         newType != null ? CanonicalTypes.createTypeWrapper(newType) : null,
         parameterInfo, null, null, null);
  }

  public ChangeSignatureProcessor(Project project,
                                  PsiMethod method,
                                  final boolean generateDelegate,
                                  @Nullable // null means unchanged
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
                                  @Nullable // null means unchanged
                                  @PsiModifier.ModifierConstant String newVisibility,
                                  String newName,
                                  CanonicalTypes.Type newType,
                                  ParameterInfoImpl @NotNull [] parameterInfo,
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
                                                   @Nullable // null means unchanged
                                                   @PsiModifier.ModifierConstant String newVisibility,
                                                   String newName,
                                                   CanonicalTypes.Type newType,
                                                   ParameterInfoImpl @NotNull [] parameterInfo,
                                                   ThrownExceptionInfo[] thrownExceptions,
                                                   Set<PsiMethod> propagateParametersMethods,
                                                   Set<PsiMethod> propagateExceptionsMethods) {
    LOG.assertTrue(method.isValid());

    if (propagateParametersMethods == null) {
      propagateParametersMethods = new HashSet<>();
    }

    if (propagateExceptionsMethods == null) {
      propagateExceptionsMethods = new HashSet<>();
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

  @Override
  @NotNull
  protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo @NotNull [] usages) {
    return new ChangeSignatureViewDescriptor(getChangeInfo().getMethod());
  }

  @Override
  public JavaChangeInfoImpl getChangeInfo() {
    return (JavaChangeInfoImpl)super.getChangeInfo();
  }

  @Override
  protected void refreshElements(PsiElement @NotNull [] elements) {
    boolean condition = elements.length == 1 && elements[0] instanceof PsiMethod;
    LOG.assertTrue(condition);
    getChangeInfo().updateMethod((PsiMethod) elements[0]);
  }

  @Override
  protected boolean preprocessUsages(@NotNull Ref<UsageInfo[]> refUsages) {
    for (ChangeSignatureUsageProcessor processor : ChangeSignatureUsageProcessor.EP_NAME.getExtensions()) {
      if (!processor.setupDefaultValues(myChangeInfo, refUsages, myProject)) return false;
    }
    final UsageInfo[] usagesIn = refUsages.get();
    Set<UsageInfo> usagesSet = ContainerUtil.set(usagesIn);

    if (myChangeInfo instanceof JavaChangeInfoImpl &&
         ((JavaChangeInfo)myChangeInfo).isVisibilityChanged() && 
         ContainerUtil.exists(usagesSet, OverriderUsageInfo.class::isInstance)) {
       String visibility = ((JavaChangeInfo)myChangeInfo).getNewVisibility();
       String oldVisibility = VisibilityUtil.getVisibilityModifier(((JavaChangeInfo)myChangeInfo).getMethod().getModifierList());
       if (oldVisibility.equals(VisibilityUtil.getHighestVisibility(visibility, oldVisibility)) &&
           (!ApplicationManager.getApplication().isUnitTestMode() && 
            Messages.showYesNoDialog(myProject, JavaRefactoringBundle.message("dialog.message.overriding.methods.with.weaken.visibility", visibility), RefactoringBundle.message("changeSignature.refactoring.name"), Messages.getQuestionIcon()) == Messages.YES)) {
         ((JavaChangeInfoImpl)myChangeInfo).propagateVisibility = true;
       }
     }
    MultiMap<PsiElement, String> conflictDescriptions = new MultiMap<>();
    collectConflictsFromExtensions(refUsages, conflictDescriptions, myChangeInfo);
    
    RenameUtil.addConflictDescriptions(usagesIn, conflictDescriptions);
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

    refUsages.set(usagesSet.toArray(UsageInfo.EMPTY_ARRAY));
    prepareSuccessful();
    return true;
  }

  private void askToRemoveCovariantOverriders(Set<? extends UsageInfo> usages) {
    if (PsiUtil.isLanguageLevel5OrHigher(myChangeInfo.getMethod())) {
      List<UsageInfo> covariantOverriderInfos = new ArrayList<>();
      for (UsageInfo usageInfo : usages) {
        if (usageInfo instanceof OverriderUsageInfo) {
          final OverriderUsageInfo info = (OverriderUsageInfo)usageInfo;
          PsiMethod overrider = Objects.requireNonNull(info.getOverridingMethod());
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
          if (overriderType != null && !type.equals(overriderType) && type.isAssignableFrom(overriderType)) {
            covariantOverriderInfos.add(usageInfo);
          }
        }
      }

      if (!covariantOverriderInfos.isEmpty()) {
        if (ApplicationManager.getApplication().isUnitTestMode() || !isProcessCovariantOverriders()) {
          for (UsageInfo usageInfo : covariantOverriderInfos) {
            usages.remove(usageInfo);
          }
        }
      }
    }
  }

  protected boolean isProcessCovariantOverriders() {
    String message = JavaRefactoringBundle.message("do.you.want.to.process.overriding.methods.with.covariant.return.type");
    return Messages.showYesNoDialog(myProject, message, RefactoringBundle.message("changeSignature.refactoring.name"), Messages.getQuestionIcon()) == Messages.YES;
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
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
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

  @NotNull
  static PsiSubstitutor calculateSubstitutor(@NotNull PsiMethod derivedMethod, @NotNull PsiMethod baseMethod) {
    PsiSubstitutor substitutor;
    if (derivedMethod.getManager().areElementsEquivalent(derivedMethod, baseMethod)) {
      substitutor = PsiSubstitutor.EMPTY;
    }
    else {
      PsiClass baseClass = baseMethod.getContainingClass();
      PsiClass derivedClass = derivedMethod.getContainingClass();
      if (baseClass != null && InheritanceUtil.isInheritorOrSelf(derivedClass, baseClass, true)) {
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
