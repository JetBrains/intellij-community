/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.refactoring.move.moveInner;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

public class MoveJavaInnerHandler implements MoveInnerHandler {
  @NotNull
  @Override
  public PsiClass copyClass(@NotNull final MoveInnerOptions options) {
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
