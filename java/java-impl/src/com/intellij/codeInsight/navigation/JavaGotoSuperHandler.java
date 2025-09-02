// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.navigation;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.generation.actions.PresentableCodeInsightActionHandler;
import com.intellij.codeInsight.navigation.actions.GotoSuperAction;
import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.ide.util.EditSourceUtil;
import com.intellij.idea.ActionsBundle;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.FindSuperElementsHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

public class JavaGotoSuperHandler implements PresentableCodeInsightActionHandler {
  @Override
  public void invoke(final @NotNull Project project, final @NotNull Editor editor, final @NotNull PsiFile psiFile) {
    FeatureUsageTracker.getInstance().triggerFeatureUsed(GotoSuperAction.FEATURE_ID);

    int offset = editor.getCaretModel().getOffset();
    new PsiTargetNavigator<>(() -> Arrays.asList(DumbService.getInstance(project).computeWithAlternativeResolveEnabled(
      () -> findSuperElements(psiFile, offset))))
      .elementsConsumer((elements, navigator) -> {
        if (!elements.isEmpty() && elements.iterator().next() instanceof PsiMethod) {
          boolean showMethodNames = !PsiUtil.allMethodsHaveSameSignature(elements.toArray(PsiMethod.EMPTY_ARRAY));
          navigator.presentationProvider(element -> GotoTargetHandler.computePresentation(element, showMethodNames));
          navigator.title(CodeInsightBundle.message("goto.super.method.chooser.title"));
        }
        else {
          navigator.title(JavaBundle.message("goto.super.class.chooser.title"));
        }
      })
      .navigate(editor, null, element -> EditSourceUtil.navigateToPsiElement(element));
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
