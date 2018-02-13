// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

/**
 * @author Pavel.Dolgov
 */
class OriginalNode extends FragmentNode {
  private final SmartPsiElementPointer<PsiElement> myStart;

  public OriginalNode(@NotNull PsiElement[] elements) {
    super(elements[0], elements[elements.length - 1]);
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(elements[0].getProject());
    myStart = smartPointerManager.createSmartPsiElementPointer(elements[0]);
  }

  @Override
  protected Navigatable getNavigatable() {
    return ObjectUtils.tryCast(myStart.getElement(), Navigatable.class);
  }
}
