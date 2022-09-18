// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiAnonymousClass;

public abstract class JavaRefactoringActionHandlerFactory {
  public static JavaRefactoringActionHandlerFactory getInstance() {
    return ApplicationManager.getApplication().getService(JavaRefactoringActionHandlerFactory.class);
  }

  /**
   * Creates handler for Anonymous To Inner refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * is not implemented.
   */
  public abstract RefactoringActionHandlerOnPsiElement<PsiAnonymousClass> createAnonymousToInnerHandler();

  /**
   * Creates handler for Pull Members Up refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either a {@link com.intellij.psi.PsiClass}, {@link com.intellij.psi.PsiField} or {@link com.intellij.psi.PsiMethod}.
   * In latter two cases, {@code elements[0]} is a member that will be preselected.
   */
  public abstract RefactoringActionHandler createPullUpHandler();

  /**
   * Creates handler for Push Members Down refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either a {@link com.intellij.psi.PsiClass}, {@link com.intellij.psi.PsiField} or {@link com.intellij.psi.PsiMethod}.
   * In latter two cases, {@code elements[0]} is a member that will be preselected.
   */
  public abstract RefactoringActionHandler createPushDownHandler();

  /**
   * Creates handler for Use Interface Where Possible refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts one {@code PsiClass}.
   */
  public abstract RefactoringActionHandler createTurnRefsToSuperHandler();

  /**
   * Creates handler for Introduce Parameter refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either one {@code PsiExpression}, that will be an initializer for introduced parameter,
   * or one {@code PsiLocalVariable}, that will be replaced with introduced parameter.
   */
  public abstract RefactoringActionHandler createIntroduceParameterHandler();

  /**
   * Creates handler for Make Method Static refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts one {@code PsiMethod}.
   */
  public abstract RefactoringActionHandler createMakeMethodStaticHandler();

  /**
   * Creates handler for Convert To Instance Method refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts one {@code PsiMethod}.
   */
  public abstract RefactoringActionHandler createConvertToInstanceMethodHandler();

  /**
   * Creates handler for Replace Constructor With Factory Method refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either a {@code PsiMethod} that is a constructor, or a {@code PsiClass}
   * with implicit default constructor.
   */
  public abstract RefactoringActionHandler createReplaceConstructorWithFactoryHandler();


  /**
   * Creates handler for Replace Constructor With Factory Method refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either a {@code PsiClass} or any number of {@code PsiField}s.
   */
  public abstract RefactoringActionHandler createEncapsulateFieldsHandler();

  /**
   * Creates handler for Replace Method Code Duplicates refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts one {@code PsiMethod}.
   */
  public abstract RefactoringActionHandler createMethodDuplicatesHandler();

  /**
   * Creates handler for Change Method/Class Signature refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either one {@code PsiMethod} or one {@code PsiClass}
   */
  public abstract RefactoringActionHandler createChangeSignatureHandler();

  /**
   * Creates handler for Extract Superclass refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts one {@code PsiClass}.
   */
  public abstract RefactoringActionHandler createExtractSuperclassHandler();

  /**
   * Creates handler for Generify (aka Type Cook) refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts any number of arbitrary {@code PsiElement}s. All code inside these elements will be generified.
   */
  public abstract RefactoringActionHandler createTypeCookHandler();

  /**
   * Creates handler for Inline refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts one inlinable {@code PsiElement} (method, local variable or constant).
   */
  public abstract RefactoringActionHandler createInlineHandler();

  /**
   * Creates handler for Extract Method refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * is not implemented.
   */
  public abstract RefactoringActionHandler createExtractMethodHandler();

  public abstract RefactoringActionHandler createInheritanceToDelegationHandler();

  /**
   * Creates handler for Extract Interface refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts one {@code PsiClass}.
   */
  public abstract RefactoringActionHandler createExtractInterfaceHandler();

  /**
   * Creates handler for Introduce Field refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either one {@code PsiExpression}, that will be an initializer for introduced field,
   * or one {@code PsiLocalVariable}, that will be replaced with introduced field.
   */
  public abstract RefactoringActionHandler createIntroduceFieldHandler();

  /**
   * Creates handler for Introduce Variable refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts one {@code PsiExpression}, that will be an initializer for introduced variable.
   */
  public abstract RefactoringActionHandler createIntroduceVariableHandler();

  /**
   * Creates handler for Introduce Constant refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either one {@code PsiExpression}, that will be an initializer for introduced constant,
   * or one {@code PsiLocalVariable}, that will be replaced with introduced constant.
   */
  public abstract RefactoringActionHandler createIntroduceConstantHandler();

  /**
   * Creates handler for Invert Boolean refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts one {@code PsiMethod}, that will be inverted
   */
  public abstract RefactoringActionHandler createInvertBooleanHandler();
}
