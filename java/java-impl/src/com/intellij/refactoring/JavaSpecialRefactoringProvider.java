// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.changeClassSignature.TypeParameterInfo;
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase;
import com.intellij.refactoring.changeSignature.JavaChangeInfo;
import com.intellij.refactoring.changeSignature.ParameterInfoImpl;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethodObject.LightMethodObjectExtractedData;
import com.intellij.refactoring.introduceVariable.JavaIntroduceEmptyVariableHandlerBase;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.refactoring.util.DocCommentPolicy;
import com.intellij.refactoring.util.classMembers.MemberInfo;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Consumer;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * WARNING! Not a real extension point, used to work around module dependencies. It is an implementation detail, may be changed without warning.
 *
 * Provides handlers used for tests and specific scenarios, as well as different utility functions.
 */
@ApiStatus.Internal
public interface JavaSpecialRefactoringProvider {
  static JavaSpecialRefactoringProvider getInstance() {
    return ApplicationManager.getApplication().getService(JavaSpecialRefactoringProvider.class);
  }

  @NotNull
  JavaIntroduceEmptyVariableHandlerBase getEmptyVariableHandler();

  @NotNull ChangeSignatureProcessorBase getChangeSignatureProcessor(Project project, JavaChangeInfo changeInfo, Runnable beforeRefactoringCallback);

  @NotNull ChangeSignatureProcessorBase getChangeSignatureProcessor(Project project,
                                                                    PsiMethod method,
                                                                    boolean generateDelegate,
                                                                    @Nullable // null means unchanged
                                                                    @PsiModifier.ModifierConstant String newVisibility,
                                                                    String newName,
                                                                    PsiType newType,
                                                                    ParameterInfoImpl[] parameterInfo,
                                                                    ThrownExceptionInfo[] exceptionInfos);

  @NotNull
  ChangeSignatureProcessorBase getChangeSignatureProcessorWithCallback(Project project,
                                                                       PsiMethod method,
                                                                       boolean generateDelegate,
                                                                       @Nullable // null means unchanged
                                                                       @PsiModifier.ModifierConstant String newVisibility,
                                                                       String newName,
                                                                       PsiType newType,
                                                                       ParameterInfoImpl @NotNull [] parameterInfo,
                                                                       boolean changeAllUsages,
                                                                       Consumer<? super List<ParameterInfoImpl>> callback);
  @NotNull
  ChangeSignatureProcessorBase getChangeSignatureProcessorWithCallback(Project project,
                                                                       PsiMethod method,
                                                                       boolean generateDelegate,
                                                                       @Nullable // null means unchanged
                                                                       @PsiModifier.ModifierConstant String newVisibility,
                                                                       String newName,
                                                                       CanonicalTypes.Type newType,
                                                                       ParameterInfoImpl @NotNull [] parameterInfo,
                                                                       ThrownExceptionInfo[] thrownExceptions,
                                                                       Set<PsiMethod> propagateParametersMethods,
                                                                       Set<PsiMethod> propagateExceptionsMethods,
                                                                       Runnable callback);

  @NotNull
  ChangeSignatureProcessorBase getUsagesAwareChangeSignatureProcessor(final Project project,
                                                                      final PsiMethod method,
                                                                      final boolean generateDelegate,
                                                                      @PsiModifier.ModifierConstant final String newVisibility,
                                                                      final String newName,
                                                                      final PsiType newType,
                                                                      final ParameterInfoImpl @NotNull [] parameterInfo,
                                                                      final UsageVisitor usageVisitor);

  void runHighlightingTypeMigration(final Project project,
                                    final Editor editor,
                                    final SearchScope boundScope,
                                    final PsiElement root,
                                    final PsiType migrationType);

  void runPullUpProcessor(@NotNull PsiClass sourceClass, PsiClass targetSuperClass, MemberInfo[] membersToMove, DocCommentPolicy javaDocPolicy);

  void runMakeMethodStaticProcessor(Project project, final PsiMethod method, boolean replaceQualifier);


  void moveClassesOrPackages(Project project,
                             PsiElement[] elements,
                             @NotNull final MoveDestination moveDestination,
                             boolean searchInComments,
                             boolean searchInNonJavaFiles,
                             MoveCallback moveCallback);



  BaseRefactoringProcessor getChangeClassSignatureProcessor(Project project, PsiClass aClass, TypeParameterInfo[] newSignature);

  LightMethodObjectExtractedData extractLightMethodObject(final Project project,
                                                          @Nullable PsiElement originalContext,
                                                          @NotNull final PsiCodeFragment fragment,
                                                          @NotNull String methodName,
                                                          @Nullable JavaSdkVersion javaVersion) throws PrepareFailedException;

  // utils which have too many deps

  PsiMethod chooseEnclosingMethod(@NotNull PsiMethod method);

  SuggestedNameInfo suggestFieldName(@Nullable PsiType defaultType,
                                     @Nullable final PsiLocalVariable localVariable,
                                     final PsiExpression initializer,
                                     final boolean forStatic,
                                     @NotNull final PsiClass parentClass);

  void collectMethodConflicts(MultiMap<PsiElement, String> conflicts, PsiMethod method, PsiParameter parameter);

  void analyzeModuleConflicts(final Project project,
                              final Collection<? extends PsiElement> scopes,
                              final UsageInfo[] usages,
                              final PsiElement target,
                              final MultiMap<PsiElement,String> conflicts);

  void analyzeModuleConflicts(final Project project,
                              final Collection<? extends PsiElement> scopes,
                              final UsageInfo[] usages,
                              final VirtualFile vFile,
                              final MultiMap<PsiElement, String> conflicts);

  boolean canBeStatic(final PsiClass targetClass, final PsiElement place, final PsiElement[] elements, Set<? super PsiField> usedFields);

  void replaceDuplicate(final Project project, final Map<PsiMember, List<Match>> duplicates, final Set<? extends PsiMember> methods);

  List<Match> hasDuplicates(final PsiElement file, final PsiMember member);

  PsiExpression inlineVariable(PsiVariable variable,
                               PsiExpression initializer,
                               PsiJavaCodeReferenceElement ref,
                               PsiExpression thisAccessExpr) throws IncorrectOperationException;

  void tryToInlineArrayCreationForVarargs(final PsiExpression expr);

  void searchForHierarchyConflicts(PsiMethod method, MultiMap<PsiElement, @Nls String> conflicts, final String modifier);

  void moveDirectoryRecursively(PsiDirectory dir, PsiDirectory destination) throws IncorrectOperationException;

  void analyzeAccessibilityConflicts(@NotNull Set<? extends PsiMember> membersToMove,
                                     @NotNull PsiClass targetClass,
                                     @NotNull MultiMap<PsiElement, String> conflicts,
                                     @Nullable String newVisibility);

  interface UsageVisitor {
    void visit(final UsageInfo usage);
    void preprocessCovariantOverriders(final List<UsageInfo> covariantOverriderInfos);
  }
}
