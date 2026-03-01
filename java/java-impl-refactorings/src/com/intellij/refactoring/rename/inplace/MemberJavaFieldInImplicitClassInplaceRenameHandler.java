// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename.inplace;

import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemberJavaFieldInImplicitClassInplaceRenameHandler extends MemberInplaceRenameHandler {

  @Override
  protected boolean isAvailable(@Nullable PsiElement element, @NotNull Editor editor, @NotNull PsiFile file) {
    return element instanceof PsiField && element.getParent() instanceof PsiImplicitClass && super.isAvailable(element, editor, file);
  }

  @Override
  protected @NotNull MemberInplaceRenamer createMemberRenamer(@NotNull PsiElement element,
                                                              @NotNull PsiNameIdentifierOwner elementToRename,
                                                              @NotNull Editor editor) {
    return new MemberInplaceRenamer(elementToRename, element, editor){
      @Override
      protected @Nullable PsiNamedElement getVariable() {
        PsiNamedElement variable = super.getVariable();
        if (variable instanceof PsiImplicitClass implicitClass) {
          for (@NotNull PsiElement child : implicitClass.getChildren()) {
            if (child.getTextRange().equals(implicitClass.getTextRange()) && child instanceof PsiNamedElement namedElement) {
              return namedElement;
            }
          }
          return null;
        }
        return variable;
      }
    };
  }
}
