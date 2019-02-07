// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.codeInsight.hint.PsiImplementationViewSession;
import com.intellij.codeInsight.navigation.BackgroundUpdaterTask;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiClassListCellRenderer;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.presentation.java.SymbolPresentationUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ShowSiblingsAction extends ShowImplementationsAction {
  @Override
  public void performForContext(@NotNull DataContext dataContext, final boolean invokedByShortcut) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);

    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = PsiImplementationViewSession.getEditor(dataContext);

    PsiElement element = PsiImplementationViewSession.getElement(project, file, editor, CommonDataKeys.PSI_ELEMENT.getData(dataContext));

    if (element == null && file == null) return;
    PsiFile containingFile = element != null ? element.getContainingFile() : file;
    if (containingFile == null || !containingFile.getViewProvider().isPhysical()) return;


    if (editor != null) {
      PsiReference ref = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
      if (element == null && ref != null) {
        element = TargetElementUtil.getInstance().adjustReference(ref);
      }
    }

    final PsiElement[] superElements =findSuperElements(element);
    if (superElements.length == 0) return;

    final boolean isMethod = superElements[0] instanceof PsiMethod;
    NavigatablePsiElement[] navigatablePsiElements = ContainerUtil.findAllAsArray(superElements, NavigatablePsiElement.class);
    final String title = "Choose super " + (isMethod ? "method" : "class or interface");
    final String findUsagesTitle = "Super " + (isMethod ? "methods" : "classes/interfaces");
    final ListCellRenderer listRenderer = isMethod ? new MethodCellRenderer(false) : new PsiClassListCellRenderer();
    final JBPopup popup = PsiElementListNavigator
      .navigateOrCreatePopup(navigatablePsiElements, title, findUsagesTitle, listRenderer, (BackgroundUpdaterTask)null,
                             objects -> showSiblings(invokedByShortcut, project, editor, file, editor != null, (PsiElement)objects[0]));
    if (popup != null) {
      if (editor != null) {
        popup.showInBestPositionFor(editor);
      } else {
        popup.showCenteredInCurrentWindow(project);
      }
    }
  }

  @Override
  protected boolean isSearchDeep() {
    return true;
  }

  private void showSiblings(boolean invokedByShortcut,
                            @NotNull Project project,
                            Editor editor,
                            PsiFile file,
                            boolean invokedFromEditor,
                            @NotNull PsiElement element) {
    final PsiElement[] impls = PsiImplementationViewSession
      .getSelfAndImplementations(editor, element, PsiImplementationViewSession.createImplementationsSearcher(true), false);
    final String text = SymbolPresentationUtil.getSymbolPresentableText(element);
    showImplementations(new PsiImplementationViewSession(project, element, impls, text, editor, file), invokedFromEditor, invokedByShortcut);
  }

  @Override
  protected boolean isIncludeAlwaysSelf() {
    return false;
  }

  @NotNull
  private static PsiElement[] findSuperElements(final PsiElement element) {
    PsiNameIdentifierOwner parent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
    if (parent == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    return FindSuperElementsHelper.findSuperElements(parent);
  }
}
