/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
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
import com.intellij.util.Consumer;
import org.jetbrains.annotations.Nullable;

public class ShowSiblingsAction extends ShowImplementationsAction {
  public ShowSiblingsAction() {
    super();
  }

  @Override
  public void performForContext(DataContext dataContext, final boolean invokedByShortcut) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final PsiFile file = CommonDataKeys.PSI_FILE.getData(dataContext);

    if (project == null) return;

    PsiDocumentManager.getInstance(project).commitAllDocuments();
    final Editor editor = getEditor(dataContext);

    PsiElement element = getElement(project, file, editor, CommonDataKeys.PSI_ELEMENT.getData(dataContext));

    if (element == null && file == null) return;
    PsiFile containingFile = element != null ? element.getContainingFile() : file;
    if (containingFile == null || !containingFile.getViewProvider().isPhysical()) return;


    if (editor != null) {
      PsiReference ref = TargetElementUtil.findReference(editor, editor.getCaretModel().getOffset());
      if (element == null && ref != null) {
        element = TargetElementUtil.getInstance().adjustReference(ref);
      }
    }

    final NavigatablePsiElement[] superElements = (NavigatablePsiElement[])findSuperElements(element);
    if (superElements == null || superElements.length == 0) return;

    final boolean isMethod = superElements[0] instanceof PsiMethod;
    final JBPopup popup = PsiElementListNavigator.navigateOrCreatePopup(superElements, "Choose super " + (isMethod ? "method" : "class or interface"), "Super " + (isMethod ? "methods" : "classes/interfaces"),
                                                                       isMethod ? new MethodCellRenderer(false) : new PsiClassListCellRenderer(), null, new Consumer<Object[]>() {
      @Override
      public void consume(Object[] objects) {
        showSiblings(invokedByShortcut, project, editor, file, editor != null, (PsiElement)objects[0]);
      }
    });
    if (popup != null) {
      if (editor != null) {
        popup.showInBestPositionFor(editor);
      } else {
        popup.showCenteredInCurrentWindow(project);
      }
    }
  }

  private void showSiblings(boolean invokedByShortcut,
                            Project project,
                            Editor editor,
                            PsiFile file,
                            boolean invokedFromEditor,
                            PsiElement element) {
    final PsiElement[] impls = getSelfAndImplementations(editor, element, createImplementationsSearcher(), false);
    final String text = SymbolPresentationUtil.getSymbolPresentableText(element);
    showImplementations(impls, project, text, editor, file, element, invokedFromEditor, invokedByShortcut);
  }

  @Override
  protected boolean isIncludeAlwaysSelf() {
    return false;
  }

  @Nullable
  private static PsiElement[] findSuperElements(final PsiElement element) {
    PsiNameIdentifierOwner parent = PsiTreeUtil.getParentOfType(element, PsiMethod.class, PsiClass.class);
    if (parent == null) {
      return null;
    }

    return FindSuperElementsHelper.findSuperElements(parent);
  }
}
