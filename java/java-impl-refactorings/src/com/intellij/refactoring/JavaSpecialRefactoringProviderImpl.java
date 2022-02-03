// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.changeClassSignature.ChangeClassSignatureProcessor;
import com.intellij.refactoring.changeClassSignature.TypeParameterInfo;
import com.intellij.refactoring.changeSignature.*;
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import com.intellij.refactoring.introduceField.InplaceIntroduceFieldPopup;
import com.intellij.refactoring.introduceParameter.IntroduceParameterHandler;
import com.intellij.refactoring.memberPullUp.PullUpProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
import com.intellij.refactoring.safeDelete.JavaSafeDeleteProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationProcessor;
import com.intellij.refactoring.typeMigration.TypeMigrationRules;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.InlineUtil;
import com.intellij.refactoring.util.RefactoringConflictsUtil;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
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
                                                                                       boolean changeAllUsages,
                                                                                       Consumer<? super List<ParameterInfoImpl>> callback) {
    return new ChangeSignatureProcessor(project, method, generateDelegate, newVisibility, newName, newType, parameterInfo) {
      @Override
      protected UsageInfo @NotNull [] findUsages() {
        return changeAllUsages ? super.findUsages() : UsageInfo.EMPTY_ARRAY;
      }

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
  public void runHighlightingTypeMigration(Project project,
                                           Editor editor,
                                           SearchScope boundScope,
                                           PsiElement root,
                                           PsiType migrationType) {
    final TypeMigrationRules rules = new TypeMigrationRules(project);
    rules.setBoundScope(boundScope);

    TypeMigrationProcessor.runHighlightingTypeMigration(project, editor, rules, root, migrationType);
  }

  @Override
  public void runPullUpProcessor(@NotNull PsiClass sourceClass,
                                 PsiClass targetSuperClass,
                                 MemberInfo[] membersToMove,
                                 DocCommentPolicy javaDocPolicy) {
    new PullUpProcessor(sourceClass, targetSuperClass, membersToMove, javaDocPolicy).run();
  }

  @Override
  public PsiExpression inlineVariable(PsiVariable variable,
                                      PsiExpression initializer,
                                      PsiJavaCodeReferenceElement ref,
                                      PsiExpression thisAccessExpr) throws IncorrectOperationException {
    return InlineUtil.inlineVariable(variable, initializer, ref, thisAccessExpr);
  }

  @Override
  public void searchForHierarchyConflicts(PsiMethod method,
                                          MultiMap<PsiElement, @Nls String> conflicts,
                                          String modifier) {
    JavaChangeSignatureUsageProcessor.ConflictSearcher.searchForHierarchyConflicts(method, conflicts, modifier);
  }

  @Override
  public void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination) throws IncorrectOperationException {
    MoveClassesOrPackagesUtil.moveDirectoryRecursively(dir, destination);
  }

  @Override
  public void analyzeAccessibilityConflicts(@NotNull Set<? extends PsiMember> membersToMove,
                                            @NotNull PsiClass targetClass,
                                            @NotNull MultiMap<PsiElement, String> conflicts,
                                            @Nullable String newVisibility) {
    RefactoringConflictsUtil.analyzeAccessibilityConflicts(membersToMove, targetClass, conflicts, newVisibility);
  }

  @Override
  public SuggestedNameInfo suggestFieldName(@Nullable PsiType defaultType,
                                            @Nullable PsiLocalVariable localVariable,
                                            PsiExpression initializer,
                                            boolean forStatic,
                                            @NotNull PsiClass parentClass) {
    return InplaceIntroduceFieldPopup.suggestFieldName(defaultType, localVariable, initializer, forStatic, parentClass);
  }

  @Override
  public void collectMethodConflicts(MultiMap<PsiElement, String> conflicts,
                                     PsiMethod method,
                                     PsiParameter parameter) {
    JavaSafeDeleteProcessor.collectMethodConflicts(conflicts, method, parameter);
  }

  @Override
  public void analyzeModuleConflicts(Project project,
                                     Collection<? extends PsiElement> scopes,
                                     UsageInfo[] usages,
                                     PsiElement target,
                                     MultiMap<PsiElement, String> conflicts) {
    RefactoringConflictsUtil.analyzeModuleConflicts(project, scopes, usages, target, conflicts);
  }

  @Override
  public void analyzeModuleConflicts(Project project,
                                     Collection<? extends PsiElement> scopes,
                                     UsageInfo[] usages,
                                     VirtualFile vFile,
                                     MultiMap<PsiElement, String> conflicts) {
    RefactoringConflictsUtil.analyzeModuleConflicts(project, scopes, usages, vFile, conflicts);
  }

  @Override
  public boolean canBeStatic(PsiClass targetClass,
                             PsiElement place,
                             PsiElement[] elements,
                             Set<? super PsiField> usedFields) {
    return ExtractMethodProcessor.canBeStatic(targetClass, place, elements, usedFields);
  }

  @Override
  public BaseRefactoringProcessor getChangeClassSignatureProcessor(Project project, PsiClass aClass, TypeParameterInfo[] newSignature) {
    return new ChangeClassSignatureProcessor(project, aClass, newSignature);
  }

  @Override
  public LightMethodObjectExtractedData extractLightMethodObject(Project project,
                                                                 @Nullable PsiElement originalContext,
                                                                 @NotNull PsiCodeFragment fragment,
                                                                 @NotNull String methodName,
                                                                 @Nullable JavaSdkVersion javaVersion) throws PrepareFailedException {
    return ExtractLightMethodObjectHandler.extractLightMethodObject(project, originalContext, fragment, methodName, javaVersion);
  }

  @Override
  public PsiMethod chooseEnclosingMethod(@NotNull PsiMethod method) {
    return IntroduceParameterHandler.chooseEnclosingMethod(method);
  }
}
