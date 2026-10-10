// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.usageView.UsageInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SafeDeleteUsageInfo extends UsageInfo {
  /// @deprecated use myReferencedElementPointer
  @Deprecated(forRemoval = true)
  private final PsiElement myReferencedElement;
  private final @Nullable SmartPsiElementPointer<PsiElement> myReferencedElementPointer;

  public SafeDeleteUsageInfo(@NotNull PsiElement element, PsiElement referencedElement) {
    super(element);
    myReferencedElement = referencedElement;
    myReferencedElementPointer = pointerOf(referencedElement);
  }

  public SafeDeleteUsageInfo(PsiElement element, PsiElement referencedElement, int startOffset, int endOffset, boolean isNonCodeUsage) {
    super(element, startOffset, endOffset, isNonCodeUsage);
    myReferencedElement = referencedElement;
    myReferencedElementPointer = pointerOf(referencedElement);
  }

  private static @Nullable SmartPsiElementPointer<PsiElement> pointerOf(@Nullable PsiElement referencedElement) {
    // A delegate may pass an element that is already gone, as the legacy flow allowed: the usage then refers to nothing.
    return referencedElement == null || !referencedElement.isValid() ? null : SmartPointerManager.createPointer(referencedElement);
  }

  /**
   * @return the referenced element, from a smart pointer: the usage can be kept after a change of PSI. Null if the element is gone, or if
   * the usage has no referenced element
   */
  public @Nullable PsiElement findReferencedElement() {
    return myReferencedElementPointer == null ? null : myReferencedElementPointer.getElement();
  }

  /**
   * @deprecated The element can be invalid after a change of PSI. Use {@link #findReferencedElement()}.
   */
  @Deprecated(forRemoval = true)
  public PsiElement getReferencedElement() {
    return myReferencedElement;
  }
}
