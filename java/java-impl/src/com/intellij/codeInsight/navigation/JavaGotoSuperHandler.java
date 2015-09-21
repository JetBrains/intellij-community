/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.codeInsight.generation.actions.PresentableCodeInsightActionHandler;
import com.intellij.codeInsight.navigation.actions.GotoSuperAction;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;

public class JavaGotoSuperHandler implements PresentableCodeInsightActionHandler {
  @Override
  public void invoke(@NotNull final Project project, @NotNull final Editor editor, @NotNull final PsiFile file) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(GotoSuperAction.FEATURE_ID);

    int offset = editor.getCaretModel().getOffset();
    PsiElement[] superElements = findSuperElements(file, offset);
    if (superElements.length == 0) return;
    if (superElements.length == 1) {
      PsiElement superElement = superElements[0].getNavigationElement();
      final PsiFile containingFile = superElement.getContainingFile();
      if (containingFile == null) return;
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) return;
      OpenFileDescriptor descriptor = new OpenFileDescriptor(project, virtualFile, superElement.getTextOffset());
      FileEditorManager.getInstance(project).openTextEditor(descriptor, true);
    }
    else if (superElements[0] instanceof PsiMethod) {
      boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature((PsiMethod[])superElements);
      PsiElementListNavigator.openTargets(editor, (PsiMethod[])superElements,
                                          CodeInsightBundle.message("goto.super.method.chooser.title"),
                                          CodeInsightBundle
                                            .message("goto.super.method.findUsages.title", ((PsiMethod)superElements[0]).getName()),
                                          new MethodCellRenderer(showMethodNames));
    }
    else {
      NavigationUtil.getPsiElementPopup(superElements, CodeInsightBundle.message("goto.super.class.chooser.title"))
        .showInBestPositionFor(editor);
    }
  }

  @NotNull
  private PsiElement[] findSuperElements(@NotNull PsiFile file, int offset) {
    PsiElement element = getElement(file, offset);
    if (element == null) return PsiElement.EMPTY_ARRAY;

    final PsiElement psiElement = PsiTreeUtil.getParentOfType(element, PsiFunctionalExpression.class, PsiMember.class);
    if (psiElement instanceof PsiFunctionalExpression) {
      final PsiMethod interfaceMethod = LambdaUtil.getFunctionalInterfaceMethod(psiElement);
      if (interfaceMethod != null) {
        return ArrayUtil.prepend(interfaceMethod, interfaceMethod.findSuperMethods(false));
      }
    }

    final PsiNameIdentifierOwner parent = PsiTreeUtil.getNonStrictParentOfType(element, PsiMethod.class, PsiClass.class);
    if (parent == null) {
      return PsiElement.EMPTY_ARRAY;
    }

    return FindSuperElementsHelper.findSuperElements(parent);
  }

  protected PsiElement getElement(@NotNull PsiFile file, int offset) {
    return file.findElementAt(offset);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void update(@NotNull Editor editor, @NotNull PsiFile file, Presentation presentation) {
    final PsiElement element = getElement(file, editor.getCaretModel().getOffset());
    final PsiElement containingElement = PsiTreeUtil.getParentOfType(element, PsiFunctionalExpression.class, PsiMember.class);
    if (containingElement instanceof PsiClass) {
      presentation.setText(ActionsBundle.actionText("GotoSuperClass"));
      presentation.setDescription(ActionsBundle.actionDescription("GotoSuperClass"));
    }
    else {
      presentation.setText(ActionsBundle.actionText("GotoSuperMethod"));
      presentation.setDescription(ActionsBundle.actionDescription("GotoSuperMethod"));
    }
    
  }
}
