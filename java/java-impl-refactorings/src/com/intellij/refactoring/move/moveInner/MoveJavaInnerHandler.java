// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveInner;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class MoveJavaInnerHandler implements MoveInnerHandler {
  @Override
  public @NotNull PsiClass copyClass(final @NotNull MoveInnerOptions options) {
    PsiClass innerClass = options.getInnerClass();

    PsiClass newClass;
    if (options.getTargetContainer() instanceof PsiDirectory) {
      newClass = createNewClass(options);
      PsiDocComment defaultDocComment = newClass.getDocComment();
      if (defaultDocComment != null && innerClass.getDocComment() == null) {
        final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(defaultDocComment.getProject());
        innerClass = (PsiClass)codeStyleManager.reformat(innerClass.addAfter(defaultDocComment, null).getParent());
      }

      newClass = (PsiClass)newClass.replace(innerClass);
      PsiUtil.setModifierProperty(newClass, PsiModifier.STATIC, false);
      PsiUtil.setModifierProperty(newClass, PsiModifier.PRIVATE, false);
      PsiUtil.setModifierProperty(newClass, PsiModifier.PROTECTED, false);
      final boolean makePublic = needPublicAccess(options.getOuterClass(), options.getTargetContainer());
      if (makePublic) {
        PsiUtil.setModifierProperty(newClass, PsiModifier.PUBLIC, true);
      }
    }
    else {
      newClass = (PsiClass)options.getTargetContainer().add(innerClass);
    }

    newClass.setName(options.getNewClassName());

    return newClass;
  }

  protected PsiClass createNewClass(MoveInnerOptions options) {
    return JavaDirectoryService.getInstance().createClass((PsiDirectory)options.getTargetContainer(), options.getNewClassName());
  }

  protected static boolean needPublicAccess(final PsiClass outerClass, final PsiElement targetContainer) {
    if (outerClass.isInterface()) {
      return true;
    }
    if (targetContainer instanceof PsiDirectory) {
      final PsiPackage targetPackage = JavaDirectoryService.getInstance().getPackage((PsiDirectory)targetContainer);
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(outerClass.getProject());
      if (targetPackage != null && !psiFacade.isInPackage(outerClass, targetPackage)) {
        return true;
      }
    }
    return false;
  }
}
