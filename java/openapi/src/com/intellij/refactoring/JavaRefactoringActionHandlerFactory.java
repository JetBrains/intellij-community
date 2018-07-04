/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.refactoring;

import com.intellij.openapi.components.ServiceManager;

public abstract class JavaRefactoringActionHandlerFactory {
  public static JavaRefactoringActionHandlerFactory getInstance() {
    return ServiceManager.getService(JavaRefactoringActionHandlerFactory.class);
  }

  /**
   * Creates handler for Anonymous To Inner refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * is not implemented.
   * @return
   */
  public abstract RefactoringActionHandler createAnonymousToInnerHandler();

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
   * accepts 1 {@code PsiClass}.
   * @return
   */
  public abstract RefactoringActionHandler createTurnRefsToSuperHandler();

  /**
   * Creates handler for Replace Temp With Query refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * is not implemented.
   */
  public abstract RefactoringActionHandler createTempWithQueryHandler();

  /**
   * Creates handler for Introduce Parameter refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either 1 {@code PsiExpression}, that will be an initializer for introduced parameter,
   * or 1 {@code PsiLocalVariable}, that will be replaced with introduced parameter.
   */
  public abstract RefactoringActionHandler createIntroduceParameterHandler();

  /**
   * Creates handler for Make Method Static refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts 1 {@code PsiMethod}.
   */
  public abstract RefactoringActionHandler createMakeMethodStaticHandler();

  /**
   * Creates handler for Convert To Instance Method refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts 1 {@code PsiMethod}.
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
   * accepts either 1 {@code PsiMethod} or 1 {@code PsiClass}
   */
  public abstract RefactoringActionHandler createChangeSignatureHandler();

  /**
   * Creates handler for Extract Superclass refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts 1 {@code PsiClass}.
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
   * accepts 1 inlinable {@code PsiElement} (method, local variable or constant).
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
   * accepts 1 {@code PsiClass}.
   */
  public abstract RefactoringActionHandler createExtractInterfaceHandler();

  /**
   * Creates handler for Introduce Field refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either 1 {@code PsiExpression}, that will be an initializer for introduced field,
   * or 1 {@code PsiLocalVariable}, that will be replaced with introduced field.
   */
  public abstract RefactoringActionHandler createIntroduceFieldHandler();

  /**
   * Creates handler for Introduce Variable refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts 1 {@code PsiExpression}, that will be an initializer for introduced variable.
   */
  public abstract RefactoringActionHandler createIntroduceVariableHandler();

  /**
   * Creates handler for Introduce Constant refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts either 1 {@code PsiExpression}, that will be an initializer for introduced constant,
   * or 1 {@code PsiLocalVariable}, that will be replaced with introduced constant.
   */
  public abstract RefactoringActionHandler createIntroduceConstantHandler();

  /**
   * Creates handler for Invert Boolean refactoring.<p>
   *
   * {@link RefactoringActionHandler#invoke(com.intellij.openapi.project.Project, com.intellij.psi.PsiElement[], com.intellij.openapi.actionSystem.DataContext)}
   * accepts 1 {@code PsiMethod}, that will be inverted
   */
  public abstract RefactoringActionHandler createInvertBooleanHandler();
}
