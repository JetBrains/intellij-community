// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.extractclass;

import com.intellij.idea.ActionsBundle;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactorJBundle;
import com.intellij.refactoring.actions.RefactoringActionContextUtil;
import com.intellij.refactoring.lang.ElementsHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ExtractClassHandler implements ElementsHandler, ContextAwareActionHandler {

  @Override
  public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
    return RefactoringActionContextUtil.isOutsideModuleAndCodeBlock(editor, file);
  }

  @Override
  public boolean isEnabledOnElements(PsiElement[] elements) {
    return ContainerUtil.exists(elements, element -> element instanceof PsiMember);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    List<PsiMember> elements = CommonRefactoringUtil.findElementsFromCaretsAndSelections(editor, file, null, e -> e instanceof PsiMember);

    PsiElement parent = PsiTreeUtil.findCommonParent(elements);
    PsiClass containingClass = parent instanceof PsiClass parentClass
                               ? parentClass
                               : PsiTreeUtil.getParentOfType(parent, PsiClass.class, false);

    final String cannotRefactorMessage = getCannotRefactorMessage(containingClass);
    if (cannotRefactorMessage != null)  {
      CommonRefactoringUtil.showErrorHint(project, editor,
                                          RefactorJBundle.message("cannot.perform.the.refactoring") + cannotRefactorMessage,
                                          getRefactoringName(), HelpID.ExtractClass);
      return;
    }
    new ExtractClassDialog(containingClass, new HashSet<>(elements)).show();
  }

  private static @Nls String getCannotRefactorMessage(PsiClass containingClass) {
    if (containingClass == null) {
      return RefactorJBundle.message("the.caret.should.be.positioned.within.a.class.to.be.refactored");
    }
    if (containingClass.isInterface()) {
      return RefactorJBundle.message("the.selected.class.is.an.interface");
    }
    if (containingClass.isEnum()) {
      return RefactorJBundle.message("the.selected.class.is.an.enumeration");
    }
    if (containingClass.isAnnotationType()) {
      return RefactorJBundle.message("the.selected.class.is.an.annotation.type");
    }
    if (classIsInner(containingClass) && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
      return RefactorJBundle.message("the.refactoring.is.not.supported.on.non.static.inner.classes");
    }
    if (classIsTrivial(containingClass)) {
      return RefactorJBundle.message("the.selected.class.has.no.members.to.extract");
    }
    if (!containingClass.getManager().isInProject(containingClass)) {
      return RefactorJBundle.message("the.selected.class.should.belong.to.project.sources");
    }
    if (containingClass instanceof PsiImplicitClass) {
      return RefactorJBundle.message("refactoring.cannot.be.done.in.implicit.class");
    }
    return null;
  }

  private static boolean classIsInner(PsiClass aClass) {
    return PsiTreeUtil.getParentOfType(aClass, PsiClass.class, true) != null;
  }

  @Override
  public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
    PsiElement parent = PsiTreeUtil.findCommonParent(elements);
    final PsiClass containingClass = parent instanceof PsiClass aClass
                                     ? aClass
                                     : PsiTreeUtil.getParentOfType(parent, PsiClass.class, false);
    final String cannotRefactorMessage = getCannotRefactorMessage(containingClass);
    if (cannotRefactorMessage != null)  {
      CommonRefactoringUtil.showErrorHint(project, null,
                                          RefactorJBundle.message("cannot.perform.the.refactoring") + cannotRefactorMessage,
                                          getRefactoringName(), HelpID.ExtractClass);
      return;
    }
    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, containingClass)) return;

    final Set<PsiElement> selectedMembers = new HashSet<>();
    Collections.addAll(selectedMembers, elements);
    new ExtractClassDialog(containingClass, selectedMembers).show();
  }

  private static boolean classIsTrivial(PsiClass containingClass) {
    if (containingClass.getFields().length == 0) {
      final PsiMethod[] methods = containingClass.getMethods();
      for (PsiMethod method : methods) {
        if (method.getBody() != null) return false;
      }
      return true;
    }
    return false;
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return ActionsBundle.message("action.ExtractClass.description");
  }
}