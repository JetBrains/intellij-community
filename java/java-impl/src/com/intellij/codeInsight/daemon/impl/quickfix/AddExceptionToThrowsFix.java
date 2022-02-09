// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.ExceptionUtil;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class AddExceptionToThrowsFix extends BaseIntentionAction implements IntentionActionWithFixAllOption {
  private final PsiElement myWrongElement;

  public AddExceptionToThrowsFix(@NotNull PsiElement wrongElement) {
    myWrongElement = wrongElement;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull final Project project, Editor editor, PsiFile file) {
    if (!FileModificationService.getInstance().prepareFileForWrite(file)) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    final List<PsiClassType> exceptions = new ArrayList<>();
    final PsiMethod targetMethod = collectExceptions(exceptions);
    if (targetMethod == null) return;

    Set<PsiClassType> unhandledExceptions = new HashSet<>(exceptions);

    addExceptionsToThrowsList(project, targetMethod, unhandledExceptions);
  }

  static void addExceptionsToThrowsList(@NotNull final Project project, @NotNull final PsiMethod targetMethod, @NotNull final Set<? extends PsiClassType> unhandledExceptions) {
    final PsiMethod[] superMethods = getSuperMethods(targetMethod);

    boolean hasSuperMethodsWithoutExceptions = hasSuperMethodsWithoutExceptions(superMethods, unhandledExceptions);

    final boolean processSuperMethods;
    if (hasSuperMethodsWithoutExceptions && superMethods.length > 0) {
      int result = ApplicationManager.getApplication().isUnitTestMode() ? Messages.YES :
                   Messages.showYesNoCancelDialog(
        QuickFixBundle.message("add.exception.to.throws.inherited.method.warning.text", targetMethod.getName()),
        QuickFixBundle.message("method.is.inherited.warning.title"),
        Messages.getQuestionIcon());

      if (result == Messages.YES) {
        processSuperMethods = true;
      }
      else if (result == Messages.NO) {
        processSuperMethods = false;
      }
      else {
        return;
      }
    }
    else {
      processSuperMethods = false;
    }

    List<PsiElement> toModify = new ArrayList<>();
    toModify.add(targetMethod);
    if (processSuperMethods) {
      Collections.addAll(toModify, superMethods);
    }
    if (!FileModificationService.getInstance().preparePsiElementsForWrite(toModify)) return;
    WriteAction.run(() -> {
      processMethod(project, targetMethod, unhandledExceptions);

      if (processSuperMethods) {
        for (PsiMethod superMethod : superMethods) {
          processMethod(project, superMethod, unhandledExceptions);
        }
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

  private static void processMethod(@NotNull Project project,
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
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PsiJavaFile)) return false;
    if (!myWrongElement.isValid()) return false;

    final List<PsiClassType> unhandled = new ArrayList<>();
    if (collectExceptions(unhandled) == null) return false;

    setText(QuickFixBundle.message("add.exception.to.throws.text", unhandled.size()));
    return true;
  }

  @Nullable
  private PsiMethod collectExceptions(List<? super PsiClassType> unhandled) {
    PsiElement targetElement = null;
    PsiMethod targetMethod = null;

    final PsiElement psiElement;
    if (myWrongElement instanceof PsiMethodReferenceExpression) {
      psiElement = myWrongElement;
    }
    else {
      PsiElement parentStatement = CommonJavaRefactoringUtil.getParentStatement(myWrongElement, false);
      if (parentStatement instanceof PsiDeclarationStatement) {
        PsiElement[] declaredElements = ((PsiDeclarationStatement)parentStatement).getDeclaredElements();
        if (declaredElements.length > 0 && declaredElements[0] instanceof PsiClass) {
          return null;
        }
      }

      psiElement = PsiTreeUtil.getParentOfType(myWrongElement, PsiFunctionalExpression.class, PsiMethod.class);
    }
    if (psiElement instanceof PsiFunctionalExpression) {
      targetMethod = LambdaUtil.getFunctionalInterfaceMethod(psiElement);
      targetElement = psiElement instanceof PsiLambdaExpression ? ((PsiLambdaExpression)psiElement).getBody() : psiElement;
    }
    else if (psiElement instanceof PsiMethod) {
      targetMethod = (PsiMethod)psiElement;
      targetElement = psiElement;
    }

    if (targetElement == null || targetMethod == null || !targetMethod.getThrowsList().isPhysical()) return null;
    if (!ExceptionUtil.canDeclareThrownExceptions(targetMethod)) return null;
    List<PsiClassType> exceptions = getUnhandledExceptions(myWrongElement, targetElement, targetMethod);
    if (exceptions == null || exceptions.isEmpty()) return null;
    unhandled.addAll(exceptions);
    return targetMethod;
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("add.exception.to.throws.family");
  }

  @Nullable
  private static List<PsiClassType> getUnhandledExceptions(@Nullable PsiElement element, PsiElement topElement, PsiMethod targetMethod) {
    if (element == null || element == topElement && !(topElement instanceof PsiMethodReferenceExpression)) return null;
    List<PsiClassType> unhandledExceptions = ExceptionUtil.getUnhandledExceptions(element);
    if (!filterInProjectExceptions(targetMethod, unhandledExceptions).isEmpty()) {
      return unhandledExceptions;
    }
    if (topElement instanceof PsiMethodReferenceExpression) {
      return null;
    }
    return getUnhandledExceptions(element.getParent(), topElement, targetMethod);
  }

  @NotNull
  private static Set<PsiClassType> filterInProjectExceptions(@Nullable PsiMethod targetMethod, @NotNull List<? extends PsiClassType> unhandledExceptions) {
    if (targetMethod == null) return Collections.emptySet();

    Set<PsiClassType> result = new HashSet<>();

    if (canModify(targetMethod)) {
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
