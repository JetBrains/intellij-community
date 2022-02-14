// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.psi.*;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessor;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class JavaSpecialRefactoringProviderImpl implements JavaSpecialRefactoringProvider {

  @Override
  public @NotNull ChangeSignatureProcessorBase getChangeSignatureProcessor(Project project,
                                                                           PsiMethod method,
                                                                           final boolean generateDelegate,
                                                                           @Nullable // null means unchanged
                                                                           @PsiModifier.ModifierConstant String newVisibility,
                                                                           String newName,
                                                                           PsiType newType,
                                                                           ParameterInfoImpl[] parameterInfo,
                                                                           ThrownExceptionInfo[] exceptionInfos) {
    return new ChangeSignatureProcessor(project, method, generateDelegate, newVisibility, newName, newType, parameterInfo, exceptionInfos);
  }

  @Override
  public @NotNull ChangeSignatureProcessorBase getChangeSignatureProcessorWithCallback(Project project,
                                                                                       PsiMethod method,
                                                                                       boolean generateDelegate,
                                                                                       @Nullable String newVisibility,
                                                                                       String newName,
                                                                                       PsiType newType,
                                                                                       ParameterInfoImpl @NotNull [] parameterInfo,
                                                                                       Consumer<? super List<ParameterInfoImpl>> callback) {
    return new ChangeSignatureProcessor(project, method, generateDelegate, newVisibility, newName, newType, parameterInfo) {
      @Override
      protected void performRefactoring(UsageInfo @NotNull [] usages) {
        CommandProcessor.getInstance().setCurrentCommandName(getCommandName());
        super.performRefactoring(usages);
        if (callback  != null) {
          callback.consume(Arrays.asList(parameterInfo));
        }
      }
    };
  }

  @Override
  public @NotNull ChangeSignatureProcessorBase getChangeSignatureProcessorWithCallback(Project project,
                                                                                       PsiMethod method,
                                                                                       boolean generateDelegate,
                                                                                       @Nullable String newVisibility,
                                                                                       String newName,
                                                                                       CanonicalTypes.Type newType,
                                                                                       ParameterInfoImpl @NotNull [] parameterInfo,
                                                                                       ThrownExceptionInfo[] thrownExceptions,
                                                                                       Set<PsiMethod> propagateParametersMethods,
                                                                                       Set<PsiMethod> propagateExceptionsMethods,
                                                                                       Runnable callback) {
    return new ChangeSignatureProcessor(project, method, generateDelegate, newVisibility, newName, newType, parameterInfo, thrownExceptions, propagateParametersMethods, propagateExceptionsMethods) {
      @Override
      protected void performRefactoring(UsageInfo @NotNull [] usages) {
        super.performRefactoring(usages);
        if (callback != null) {
          callback.run();
        }
      }
    };
  }

  @Override
  public void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination) throws IncorrectOperationException {
    MoveClassesOrPackagesUtil.moveDirectoryRecursively(dir, destination);
  }

  @Override
  public LightMethodObjectExtractedData extractLightMethodObject(Project project,
                                                                 @Nullable PsiElement originalContext,
                                                                 @NotNull PsiCodeFragment fragment,
                                                                 @NotNull String methodName,
                                                                 @Nullable JavaSdkVersion javaVersion) throws PrepareFailedException {
    return ExtractLightMethodObjectHandler.extractLightMethodObject(project, originalContext, fragment, methodName, javaVersion);
  }
}
