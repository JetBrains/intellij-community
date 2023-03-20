// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.refactoring.changeClassSignature.TypeParameterInfo;
import com.intellij.refactoring.changeSignature.ParameterInfo;
import com.intellij.refactoring.changeSignature.ThrownExceptionInfo;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

public abstract class JavaRefactoringFactory extends RefactoringFactory {
  public static JavaRefactoringFactory getInstance(Project project) {
    return (JavaRefactoringFactory)project.getService(RefactoringFactory.class);
  }

  @Override
  public abstract JavaRenameRefactoring createRename(@NotNull PsiElement element, String newName);

  @Nullable("in case the source file is not located under any source root")
  public abstract MoveInnerRefactoring createMoveInner(PsiClass innerClass, String newName,
                                                       boolean passOuterClass, String parameterName);

  /**
   * Creates move destination for a specified package that preserves source folders for moved items.
   */
  public abstract MoveDestination createSourceFolderPreservingMoveDestination(@NotNull String targetPackageQualifiedName);

  /**
   * Creates move destination for a specified package that moves all items to a specified source folder
   */
  public abstract MoveDestination createSourceRootMoveDestination(@NotNull String targetPackageQualifiedName, @NotNull VirtualFile sourceRoot);

  public MoveClassesOrPackagesRefactoring createMoveClassesOrPackages(PsiElement[] elements, MoveDestination moveDestination) {
    return createMoveClassesOrPackages(elements, moveDestination, true, true);
  }

  public abstract MoveClassesOrPackagesRefactoring createMoveClassesOrPackages(PsiElement[] elements,
                                                                               MoveDestination moveDestination,
                                                                               boolean searchInComments, 
                                                                               boolean searchInNonJavaFiles);

  public abstract MoveMembersRefactoring createMoveMembers(PsiMember[] elements,
                                                           String targetClassQualifiedName,
                                                           String newVisibility);

  public abstract MoveMembersRefactoring createMoveMembers(PsiMember[] elements,
                                                           String targetClassQualifiedName,
                                                           String newVisibility,
                                                           boolean makeEnumConstants);

  public abstract MakeStaticRefactoring<PsiMethod> createMakeMethodStatic(PsiMethod method,
                                                                          boolean replaceUsages,
                                                                          String classParameterName,
                                                                          @NotNull PsiField[] fields,
                                                                          String[] names);

  public abstract MakeStaticRefactoring<PsiClass> createMakeClassStatic(PsiClass aClass,
                                                                        boolean replaceUsages,
                                                                        String classParameterName,
                                                                        PsiField[] fields,
                                                                        String[] names);

  public abstract ConvertToInstanceMethodRefactoring createConvertToInstanceMethod(PsiMethod method,
                                                                                   PsiParameter targetParameter);

  public abstract TurnRefsToSuperRefactoring createTurnRefsToSuper(PsiClass aClass,
                                                                   PsiClass aSuper,
                                                                   boolean replaceInstanceOf);

  public abstract ReplaceConstructorWithFactoryRefactoring createReplaceConstructorWithFactory(PsiMethod method,
                                                                                               PsiClass targetClass,
                                                                                               String factoryName);

  public abstract ReplaceConstructorWithFactoryRefactoring createReplaceConstructorWithFactory(PsiClass originalClass,
                                                                                               PsiClass targetClass,
                                                                                               String factoryName);

  public abstract ChangeClassSignatureRefactoring createChangeClassSignatureProcessor(Project project, PsiClass aClass, TypeParameterInfo[] newSignature);
  
  public abstract ChangeSignatureRefactoring createChangeSignatureProcessor(PsiMethod method,
                                                                            boolean generateDelegate,
                                                                            @Nullable // null means unchanged
                                                                            @PsiModifier.ModifierConstant String newVisibility,
                                                                            String newName,
                                                                            PsiType newReturnType,
                                                                            ParameterInfo @NotNull [] parameterInfo,
                                                                            ThrownExceptionInfo[] thrownExceptions,
                                                                            Set<PsiMethod> propagateParametersMethods,
                                                                            Set<PsiMethod> propagateExceptionsMethods,
                                                                            Consumer<? super List<ParameterInfo>> callback);
}
