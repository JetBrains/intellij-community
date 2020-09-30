// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.PsiElementListNavigator;
import com.intellij.codeInsight.generation.actions.PresentableCodeInsightActionHandler;
import com.intellij.codeInsight.navigation.actions.GotoSuperAction;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.MethodCellRenderer;
import com.intellij.ide.util.PsiNavigationSupport;
import com.intellij.idea.ActionsBundle;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
      Navigatable descriptor =
        PsiNavigationSupport.getInstance().createNavigatable(project, virtualFile, superElement.getTextOffset());
      descriptor.navigate(true);
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
      NavigationUtil.getPsiElementPopup(superElements, JavaBundle.message("goto.super.class.chooser.title"))
        .showInBestPositionFor(editor);
    }
  }

  private PsiElement @NotNull [] findSuperElements(@NotNull PsiFile file, int offset) {
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
    update(editor, file, presentation, null);
  }

  @Override
  public void update(@NotNull Editor editor, @NotNull PsiFile file, Presentation presentation, @Nullable String actionPlace) {
    final PsiElement element = getElement(file, editor.getCaretModel().getOffset());
    final PsiElement containingElement = PsiTreeUtil.getParentOfType(element, PsiFunctionalExpression.class, PsiMember.class);
    boolean useShortName = actionPlace != null && (ActionPlaces.MAIN_MENU.equals(actionPlace) || ActionPlaces.isPopupPlace(actionPlace));
    if (containingElement instanceof PsiClass) {
      presentation.setText(JavaBundle.message(useShortName ? "action.GotoSuperClass.MainMenu.text" : "action.GotoSuperClass.text"));
      presentation.setDescription(JavaBundle.message("action.GotoSuperClass.description"));
    }
    else {
      presentation.setText(ActionsBundle.actionText(useShortName ? "GotoSuperMethod.MainMenu" : "GotoSuperMethod"));
      presentation.setDescription(ActionsBundle.actionDescription("GotoSuperMethod"));
    }
  }
}
