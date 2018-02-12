/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.refactoring;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author dsl
 */
public abstract class JavaRefactoringFactory extends RefactoringFactory {
  public static JavaRefactoringFactory getInstance(Project project) {
    return (JavaRefactoringFactory) ServiceManager.getService(project, RefactoringFactory.class);
  }

  public abstract JavaRenameRefactoring createRename(@NotNull PsiElement element, String newName);

  @Nullable("in case the source file is not located under any source root")
  public abstract MoveInnerRefactoring createMoveInner(PsiClass innerClass, String newName,
                                                       boolean passOuterClass, String parameterName);

  /**
   * Creates move destination for a specified package that preserves source folders for moved items.
   */
  public abstract MoveDestination createSourceFolderPreservingMoveDestination(@NotNull String targetPackageQualifiedName);

  /**
   * Creates move destination for a specified package that moves all items to a specifed source folder
   */
  public abstract MoveDestination createSourceRootMoveDestination(@NotNull String targetPackageQualifiedName, @NotNull VirtualFile sourceRoot);

  public abstract MoveClassesOrPackagesRefactoring createMoveClassesOrPackages(PsiElement[] elements, MoveDestination moveDestination);

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
                                                                          PsiField[] fields,
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

  public abstract TypeCookRefactoring createTypeCook(PsiElement[] elements,
                                                     boolean dropObsoleteCasts,
                                                     boolean leaveObjectsRaw,
                                                     boolean preserveRawArrays,
                                                     boolean exhaustive,
                                                     boolean cookObjects,
                                                     boolean cookToWildcards);

  /**
   * Creates Introduce Parameter refactoring that replaces local variable with parameter.
   * @param methodToReplaceIn Method that the local variable should be replaced in.
   * @param methodToSearchFor Method that usages of should be updated (for overriding methods)
   * @param parameterName Name of new parameter.
   * @param parameterInitializer Initializer to use in method calls.
   * @param localVariable local variable that will be replaced
   * @param removeLocalVariable should local variable be removed
   * @param declareFinal should created parameter be declared {@code final}
   */
  public abstract IntroduceParameterRefactoring createIntroduceParameterRefactoring(PsiMethod methodToReplaceIn,
                                                                                    PsiMethod methodToSearchFor,
                                                                                    String parameterName, PsiExpression parameterInitializer,
                                                                                    PsiLocalVariable localVariable,
                                                                                    boolean removeLocalVariable, boolean declareFinal);

  /**
   * Creates Introduce Parameter refactoring that replaces expression with parameter.
   * @param methodToReplaceIn Method that the local variable should be replaced in.
   * @param methodToSearchFor Method that usages of should be updated (for overriding methods)
   * @param parameterName Name of new parameter.
   * @param parameterInitializer Initializer to use in method calls.
   * @param expressionToSearchFor expression that should be replaced with parameters
   * @param declareFinal should created parameter be declared {@code final}
   * @param replaceAllOccurences should all occurences of expression be replaced
   */
  public abstract IntroduceParameterRefactoring createIntroduceParameterRefactoring(PsiMethod methodToReplaceIn,
                                                                                    PsiMethod methodToSearchFor,
                                                                                    String parameterName,
                                                                                    PsiExpression parameterInitializer,
                                                                                    PsiExpression expressionToSearchFor,
                                                                                    boolean declareFinal,
                                                                                    final boolean replaceAllOccurences);
}
