// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.openapi.impl;

import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.refactoring.*;
import com.intellij.refactoring.changeClassSignature.TypeParameterInfo;
import com.intellij.refactoring.changeSignature.*;
import com.intellij.refactoring.move.moveClassesOrPackages.AutocreatingSingleSourceRootMoveDestination;
import com.intellij.refactoring.move.moveClassesOrPackages.MultipleRootsMoveDestination;
import com.intellij.refactoring.move.moveInner.MoveInnerImpl;
import com.intellij.refactoring.util.CanonicalTypes;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class JavaRefactoringFactoryImpl extends JavaRefactoringFactory {
  private static final Logger LOG = Logger.getInstance(JavaRefactoringFactoryImpl.class);
  private final Project myProject;

  public JavaRefactoringFactoryImpl(Project project) {
    myProject = project;
  }

  @Override
  public JavaRenameRefactoring createRename(@NotNull PsiElement element, String newName) {
    return new JavaRenameRefactoringImpl(myProject, element, newName, true, true);
  }

  @Override
  public RenameRefactoring createRename(@NotNull PsiElement element, String newName, boolean searchInComments, boolean searchInNonJavaFiles) {
    return new JavaRenameRefactoringImpl(myProject, element, newName, searchInComments, searchInNonJavaFiles);
  }

  @Override
  public RenameRefactoring createRename(@NotNull PsiElement element,
                                        String newName,
                                        SearchScope scope,
                                        boolean searchInComments, boolean searchInNonJavaFiles) {
    return new JavaRenameRefactoringImpl(myProject, element, newName, scope, searchInComments, searchInNonJavaFiles);
  }

  @Override
  public MoveInnerRefactoring createMoveInner(PsiClass innerClass, String newName, boolean passOuterClass, String parameterName) {
    final PsiElement targetContainer = MoveInnerImpl.getTargetContainer(innerClass, false);
    if (targetContainer == null) return null;
    return new MoveInnerRefactoringImpl(myProject, innerClass, newName, passOuterClass, parameterName, targetContainer);
  }

  @Override
  public MoveDestination createSourceFolderPreservingMoveDestination(@NotNull String targetPackage) {
    return new MultipleRootsMoveDestination(createPackageWrapper(targetPackage));
  }

  private PackageWrapper createPackageWrapper(@NotNull String targetPackage) {
    return new PackageWrapper(PsiManager.getInstance(myProject), targetPackage);
  }

  @Override
  public MoveDestination createSourceRootMoveDestination(@NotNull String targetPackageQualifiedName, @NotNull VirtualFile sourceRoot) {
    final PsiDirectory directory = PsiManager.getInstance(myProject).findDirectory(sourceRoot);
    LOG.assertTrue(directory != null && JavaDirectoryService.getInstance().isSourceRoot(directory), "Should pass source root");
    return new AutocreatingSingleSourceRootMoveDestination(createPackageWrapper(targetPackageQualifiedName),
                                                           sourceRoot);
  }


  @Override
  public MoveClassesOrPackagesRefactoring createMoveClassesOrPackages(PsiElement[] elements,
                                                                      MoveDestination moveDestination,
                                                                      boolean searchInComments, 
                                                                      boolean searchInNonJavaFiles) {
    return new MoveClassesOrPackagesRefactoringImpl(myProject, elements, moveDestination, searchInComments, searchInNonJavaFiles);
  }

  @Override
  public MoveMembersRefactoring createMoveMembers(final PsiMember[] elements,
                                                  final String targetClassQualifiedName,
                                                  final String newVisibility) {
    return createMoveMembers(elements, targetClassQualifiedName, newVisibility, false);
  }

  @Override
  public MoveMembersRefactoring createMoveMembers(final PsiMember[] elements,
                                                  final String targetClassQualifiedName,
                                                  final String newVisibility,
                                                  final boolean makeEnumConstants) {
    return new MoveMembersRefactoringImpl(myProject, elements, targetClassQualifiedName, newVisibility, makeEnumConstants);
  }

  @Override
  public MakeStaticRefactoring<PsiMethod> createMakeMethodStatic(PsiMethod method,
                                                                 boolean replaceUsages,
                                                                 String classParameterName,
                                                                 @NotNull PsiField @NotNull [] fields,
                                                                 String[] names) {
    return new MakeMethodStaticRefactoringImpl(myProject, method, replaceUsages, classParameterName, fields, names);
  }

  @Override
  public MakeStaticRefactoring<PsiClass> createMakeClassStatic(PsiClass aClass,
                                                               boolean replaceUsages,
                                                               String classParameterName,
                                                               PsiField[] fields,
                                                               String[] names) {
    return new MakeClassStaticRefactoringImpl(myProject, aClass, replaceUsages, classParameterName, fields, names);
  }

  @Override
  public ConvertToInstanceMethodRefactoring createConvertToInstanceMethod(PsiMethod method,
                                                                          PsiParameter targetParameter) {
    return new ConvertToInstanceMethodRefactoringImpl(myProject, method, targetParameter);
  }

  @Override
  public SafeDeleteRefactoring createSafeDelete(PsiElement[] elements) {
    return new SafeDeleteRefactoringImpl(myProject, elements);
  }

  @Override
  public TurnRefsToSuperRefactoring createTurnRefsToSuper(PsiClass aClass,
                                                          PsiClass aSuper,
                                                          boolean replaceInstanceOf) {
    return new TurnRefsToSuperRefactoringImpl(myProject, aClass, aSuper, replaceInstanceOf);
  }

  @Override
  public ReplaceConstructorWithFactoryRefactoring createReplaceConstructorWithFactory(PsiMethod method,
                                                                                      PsiClass targetClass,
                                                                                      String factoryName) {
    return new ReplaceConstructorWithFactoryRefactoringImpl(myProject, method, targetClass, factoryName);
  }

  @Override
  public ReplaceConstructorWithFactoryRefactoring createReplaceConstructorWithFactory(PsiClass originalClass,
                                                                                      PsiClass targetClass,
                                                                                      String factoryName) {
    return new ReplaceConstructorWithFactoryRefactoringImpl(myProject, originalClass, targetClass, factoryName);
  }

  @Override
  public ChangeClassSignatureRefactoringImpl createChangeClassSignatureProcessor(Project project,
                                                                           PsiClass aClass,
                                                                           TypeParameterInfo[] newSignature) {
    return new ChangeClassSignatureRefactoringImpl(project, aClass, newSignature);
  }

  @Override
  public ChangeSignatureRefactoring createChangeSignatureProcessor(PsiMethod method,
                                                                   boolean generateDelegate,
                                                                   @Nullable String newVisibility,
                                                                   String newName,
                                                                   PsiType newReturnType,
                                                                   ParameterInfo @NotNull [] parameterInfo,
                                                                   ThrownExceptionInfo[] thrownExceptions,
                                                                   Set<PsiMethod> propagateParametersMethods,
                                                                   Set<PsiMethod> propagateExceptionsMethods,
                                                                   Consumer<? super List<ParameterInfo>> callback) {
    JavaChangeInfo changeInfo = JavaChangeInfoImpl.generateChangeInfo(method, generateDelegate, newVisibility,
                                                                      newName, newReturnType != null ? CanonicalTypes.createTypeWrapper(newReturnType) : null,
                                                                      (ParameterInfoImpl[])parameterInfo,
                                                                      thrownExceptions, propagateParametersMethods, propagateExceptionsMethods);
    return new ChangeSignatureRefactoringImpl(new ChangeSignatureProcessor(myProject, changeInfo) {
      @Override
      protected void performRefactoring(UsageInfo @NotNull [] usages) {
        CommandProcessor.getInstance().setCurrentCommandName(getCommandName());
        super.performRefactoring(usages);
        if (callback  != null) {
          callback.consume(Arrays.asList(parameterInfo));
        }
      }
    });
  }
}
