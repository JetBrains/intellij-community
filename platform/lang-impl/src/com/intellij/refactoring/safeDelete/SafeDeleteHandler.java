// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.safeDelete.api.SafeDeleteTarget;
import com.intellij.refactoring.safeDelete.api.SafeDeleteTargetProvider;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import static com.intellij.refactoring.safeDelete.impl.SafeDeleteKt.safeDelete;

public final class SafeDeleteHandler implements RefactoringActionHandler {
  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = CommonDataKeys.PSI_ELEMENT.getData(dataContext);
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    if (element == null || !SafeDeleteProcessor.validElement(element)) {
      String message = RefactoringBundle.getCannotRefactorMessage(RefactoringBundle.message("is.not.supported.in.the.current.context",
                                                                                            getRefactoringName()));
      CommonRefactoringUtil.showErrorHint(project, editor, message, getRefactoringName(), "refactoring.safeDelete");
      return;
    }
    invoke(project, new PsiElement[]{element}, dataContext);
  }

  @Override
  public void invoke(@NotNull Project project, @NotNull PsiElement @NotNull [] elements, DataContext dataContext) {
    invoke(project, elements, PlatformCoreDataKeys.MODULE.getData(dataContext), true, null, null);
  }

  public static void invoke(@NotNull Project project, @NotNull PsiElement @NotNull [] elements, boolean checkDelegates) {
    invoke(project, elements, checkDelegates, null);
  }

  public static void invoke(@NotNull Project project, PsiElement @NotNull [] elements, boolean checkDelegates, @Nullable Runnable successRunnable) {
    invoke(project, elements, null, checkDelegates, successRunnable, null);
  }

  public static void invoke(@NotNull Project project, PsiElement @NotNull [] elements, @Nullable Module module, boolean checkDelegates,
                            @Nullable Runnable successRunnable) {
    invoke(project, elements, module, checkDelegates, successRunnable, null);
  }

  public static void invoke(@NotNull Project project, PsiElement @NotNull [] elements, @Nullable Module module, boolean checkDelegates,
                            @Nullable Runnable successRunnable, @Nullable Runnable afterRefactoring) {
    invoke(project, elements, module, checkDelegates, successRunnable, afterRefactoring, false);
  }

  public static void invoke(@NotNull Project project, PsiElement @NotNull [] elements, @Nullable Module module, boolean checkDelegates,
                            @Nullable Runnable successRunnable, @Nullable Runnable afterRefactoring, boolean silent) {
    for (PsiElement element : elements) {
      if (!SafeDeleteProcessor.validElement(element)) {
        return;
      }
    }

    if (elements.length == 1) {
      SafeDeleteTarget target = SafeDeleteTargetProvider.Companion.createSafeDeleteTarget(elements[0]);
      if (target != null) {
        safeDelete(project, target.createPointer());
        return;
      }
    }

    PsiElement[] tempToDelete = PsiTreeUtil.filterAncestors(elements);
    Set<PsiElement> elementsSet = Set.of(tempToDelete);
    Set<PsiElement> fullElementsSet = new LinkedHashSet<>();

    if (checkDelegates) {
      for (PsiElement element : tempToDelete) {
        boolean found = false;
        for (SafeDeleteProcessorDelegate delegate: SafeDeleteProcessorDelegate.EP_NAME.getExtensionList()) {
          if (delegate.handlesElement(element)) {
            found = true;
            Collection<? extends PsiElement> addElements = delegate instanceof SafeDeleteProcessorDelegateBase base
                                                           ? base.getElementsToSearch(element, module, elementsSet)
                                                           : delegate.getElementsToSearch(element, elementsSet);
            if (addElements == null) return;
            fullElementsSet.addAll(addElements);
            break;
          }
        }
        if (!found) {
          fullElementsSet.add(element);
        }
      }
    } else {
      ContainerUtil.addAll(fullElementsSet, tempToDelete);
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, fullElementsSet, true)) return;

    PsiElement[] elementsToDelete = PsiUtilCore.toPsiElementArray(fullElementsSet);

    if (ApplicationManager.getApplication().isUnitTestMode() || silent) {
      RefactoringSettings settings = RefactoringSettings.getInstance();
      SafeDeleteProcessor processor =
        SafeDeleteProcessor.createInstance(project, null, elementsToDelete, settings.SAFE_DELETE_SEARCH_IN_COMMENTS,
                                           settings.SAFE_DELETE_SEARCH_IN_NON_JAVA, true);
      if (afterRefactoring != null) processor.setAfterRefactoringCallback(afterRefactoring);
      processor.run();
      if (successRunnable != null) successRunnable.run();
    }
    else {
      SafeDeleteDialog.Callback callback = dialog -> {
        SafeDeleteProcessor processor = SafeDeleteProcessor.createInstance(project, () -> {
          if (successRunnable != null) {
            successRunnable.run();
          }
          dialog.close(DialogWrapper.CANCEL_EXIT_CODE);
        }, elementsToDelete, dialog.isSearchInComments(), dialog.isSearchForTextOccurences(), true);
        if (afterRefactoring != null) processor.setAfterRefactoringCallback(afterRefactoring);
        processor.run();
      };

      SafeDeleteDialog dialog = new SafeDeleteDialog(project, elementsToDelete, callback);
      dialog.show();
    }
  }

  public static @NlsContexts.DialogTitle @NotNull String getRefactoringName() {
    return RefactoringBundle.message("safe.delete.title");
  }
}