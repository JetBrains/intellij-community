// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiBasedModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ThreeState;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class AddExceptionToThrowsFix extends PsiBasedModCommandAction<PsiElement> {
  private final @NotNull ThreeState myProcessHierarchy;
  private final Collection<PsiClassType> myExceptionsToAdd;

  public AddExceptionToThrowsFix(@NotNull PsiElement wrongElement) {
    this(wrongElement, ThreeState.UNSURE);
  }

  public AddExceptionToThrowsFix(@NotNull PsiElement wrongElement, @NotNull Collection<PsiClassType> exceptionsToAdd) {
    super(wrongElement);
    myExceptionsToAdd = exceptionsToAdd;
    myProcessHierarchy = ThreeState.UNSURE;
  }

  public AddExceptionToThrowsFix(@NotNull PsiElement wrongElement, @NotNull ThreeState hierarchy) {
    super(wrongElement);
    myExceptionsToAdd = List.of();
    myProcessHierarchy = hierarchy;
  }

  @Override
  protected @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull PsiElement element) {
    final Set<PsiClassType> exceptions = new HashSet<>();
    final PsiMethod targetMethod = collectExceptions(exceptions, element);
    if (targetMethod == null) return ModCommand.nop();
    ModCommand command = addExceptionsToThrowsList(context.project(), targetMethod, exceptions, myProcessHierarchy);
    if (command == null) {
      return ModCommand.chooseAction(QuickFixBundle.message("add.exception.to.throws.header", exceptions.size()),
                                     new AddExceptionToThrowsFix(element, ThreeState.YES),
                                     new AddExceptionToThrowsFix(element, ThreeState.NO));
    }

    return command;
  }

  static @Nullable ModCommand addExceptionsToThrowsList(final @NotNull Project project,
                                                        final @NotNull PsiMethod targetMethod,
                                                        final @NotNull Set<? extends PsiClassType> unhandledExceptions,
                                                        @NotNull ThreeState processHierarchy) {
    final PsiMethod[] superMethods = processHierarchy == ThreeState.NO ? PsiMethod.EMPTY_ARRAY : getSuperMethods(targetMethod);

    boolean hasSuperMethodsWithoutExceptions = hasSuperMethodsWithoutExceptions(superMethods, unhandledExceptions);

    final boolean processSuperMethods;
    if (hasSuperMethodsWithoutExceptions && superMethods.length > 0) {
      if (processHierarchy == ThreeState.UNSURE) return null;
      processSuperMethods = true;
    }
    else {
      processSuperMethods = false;
    }

    List<PsiMethod> toModify = new ArrayList<>();
    toModify.add(targetMethod);
    if (processSuperMethods) {
      Collections.addAll(toModify, superMethods);
    }
    return ModCommand.psiUpdate(targetMethod, (e, updater) -> {
      for (PsiMethod method : ContainerUtil.map(toModify, updater::getWritable)) {
        processMethod(project, method, unhandledExceptions);
      }
    });
  }

  private static PsiMethod @NotNull [] getSuperMethods(@NotNull PsiMethod targetMethod) {
    List<PsiMethod> result = new ArrayList<>();
    collectSuperMethods(targetMethod, result);
    return result.toArray(PsiMethod.EMPTY_ARRAY);
  }

  private static void collectSuperMethods(@NotNull PsiMethod method, @NotNull List<? super PsiMethod> result) {
    PsiMethod[] superMethods = method.findSuperMethods();
    for (PsiMethod superMethod : superMethods) {
      result.add(superMethod);
      collectSuperMethods(superMethod, result);
    }
  }

  private static boolean hasSuperMethodsWithoutExceptions(PsiMethod @NotNull [] superMethods, @NotNull Set<? extends PsiClassType> unhandledExceptions) {
    for (PsiMethod superMethod : superMethods) {
      PsiClassType[] referencedTypes = superMethod.getThrowsList().getReferencedTypes();

      Set<PsiClassType> exceptions = new HashSet<>(unhandledExceptions);
      for (PsiClassType referencedType : referencedTypes) {
        for (PsiClassType exception : unhandledExceptions) {
          if (referencedType.isAssignableFrom(exception)) exceptions.remove(exception);
        }
      }

      if (!exceptions.isEmpty()) return true;
    }

    return false;
  }

  public static void processMethod(@NotNull Project project,
                                   @NotNull PsiMethod targetMethod,
                                   @NotNull Set<? extends PsiClassType> unhandledExceptions) throws IncorrectOperationException {
    for (PsiClassType unhandledException : unhandledExceptions) {
      PsiClass exceptionClass = unhandledException.resolve();
      if (exceptionClass != null) {
        PsiUtil.addException(targetMethod, exceptionClass);
      }
    }

    CodeStyleManager.getInstance(project).reformat(targetMethod.getThrowsList());
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiElement element) {
    if (!(context.file() instanceof PsiJavaFile)) return null;
    final Set<PsiClassType> unhandled = new HashSet<>();
    if (collectExceptions(unhandled, element) == null) return null;
    return switch (myProcessHierarchy) {
      case UNSURE -> Presentation.of(QuickFixBundle.message("add.exception.to.throws.text", unhandled.size()))
        .withFixAllOption(this);
      case YES -> Presentation.of(QuickFixBundle.message("add.exception.to.throws.hierarchy"));
      case NO -> Presentation.of(QuickFixBundle.message("add.exception.to.throws.only.this"));
    };
  }

  static boolean isAnyOfTheMethodsUnmodifiable(@NotNull PsiMethod targetMethod) {
    return ContainerUtil.or(getSuperMethods(targetMethod), method -> method instanceof PsiCompiledElement || method instanceof SyntheticElement);
  }

  private @Nullable PsiMethod collectExceptions(Set<? super PsiClassType> unhandled, PsiElement element) {
    PsiElement targetElement = null;
    PsiMethod targetMethod = null;

    final PsiElement psiElement;
    if (element instanceof PsiMethodReferenceExpression || element instanceof PsiMethod) {
      psiElement = element;
    }
    else {
      PsiElement parentStatement = CommonJavaRefactoringUtil.getParentStatement(element, false);
      if (parentStatement instanceof PsiDeclarationStatement declaration) {
        PsiElement[] declaredElements = declaration.getDeclaredElements();
        if (declaredElements.length > 0 && declaredElements[0] instanceof PsiClass) {
          return null;
        }
      }

      psiElement = PsiTreeUtil.getParentOfType(element, PsiFunctionalExpression.class, PsiMethod.class);
    }
    if (psiElement instanceof PsiFunctionalExpression) {
      targetMethod = LambdaUtil.getFunctionalInterfaceMethod(psiElement);
      targetElement = psiElement instanceof PsiLambdaExpression lambda ? lambda.getBody() : psiElement;
    }
    else if (psiElement instanceof PsiMethod method) {
      targetMethod = method;
      targetElement = psiElement;
    }

    if (targetElement == null || targetMethod == null) return null;
    if (!ExceptionUtil.canDeclareThrownExceptions(targetMethod)) return null;
    Collection<PsiClassType> exceptions;
    if (!myExceptionsToAdd.isEmpty()) {
      if (ContainerUtil.exists(myExceptionsToAdd, e -> !e.isValid())) return null;
      exceptions = myExceptionsToAdd;
    }
    else {
      exceptions = getUnhandledExceptions(element, targetElement, targetMethod);
    } 
    if (exceptions == null || exceptions.isEmpty()) return null;
    unhandled.addAll(exceptions);
    return targetMethod;
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("add.exception.to.throws.family");
  }

  private static @Nullable List<PsiClassType> getUnhandledExceptions(@Nullable PsiElement element, PsiElement topElement, PsiMethod targetMethod) {
    if (element == null || element instanceof PsiFile || element == topElement && !(topElement instanceof PsiMethodReferenceExpression) && !(topElement instanceof PsiMethod)) return null;
    List<PsiClassType> unhandledExceptions = ExceptionUtil.getUnhandledExceptions(element);
     if (!filterInProjectExceptions(targetMethod, unhandledExceptions).isEmpty()) {
      return unhandledExceptions;
    }
    if (topElement instanceof PsiMethodReferenceExpression) {
      return null;
    }
    return getUnhandledExceptions(element.getParent(), topElement, targetMethod);
  }

  private static @NotNull Set<PsiClassType> filterInProjectExceptions(@Nullable PsiMethod targetMethod, @NotNull List<? extends PsiClassType> unhandledExceptions) {
    if (targetMethod == null) return Collections.emptySet();

    Set<PsiClassType> result = new HashSet<>();

    if (BaseIntentionAction.canModify(targetMethod)) {
      PsiMethod[] superMethods = targetMethod.findSuperMethods();
      for (PsiMethod superMethod : superMethods) {
        Set<PsiClassType> classTypes = filterInProjectExceptions(superMethod, unhandledExceptions);
        result.addAll(classTypes);
      }

      if (superMethods.length == 0) {
        result.addAll(unhandledExceptions);
      }
    }
    else {
      PsiClassType[] referencedTypes = targetMethod.getThrowsList().getReferencedTypes();
      for (PsiClassType referencedType : referencedTypes) {
        PsiClass psiClass = referencedType.resolve();
        if (psiClass == null) continue;
        for (PsiClassType exception : unhandledExceptions) {
          if (referencedType.isAssignableFrom(exception)) result.add(exception);
        }
      }
    }

    return result;
  }
}
