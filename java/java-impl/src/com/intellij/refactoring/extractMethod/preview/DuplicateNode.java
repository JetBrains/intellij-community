// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.preview;

import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.util.duplicates.Match;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Pavel.Dolgov
 */
public class DuplicateNode extends FragmentNode {
  private final SmartPsiElementPointer<PsiElement> myStart;
  private final SmartPsiElementPointer<PsiElement> myEnd;
  private boolean myExcluded;

  public DuplicateNode(@NotNull Match duplicate) {
    super(duplicate.getMatchStart(), duplicate.getMatchEnd());
    PsiElement start = duplicate.getMatchStart();
    PsiElement end = duplicate.getMatchEnd();
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(start.getProject());
    myStart = smartPointerManager.createSmartPsiElementPointer(start);
    myEnd = smartPointerManager.createSmartPsiElementPointer(end);
  }

  @Nullable
  public PsiElement getStart() {
    return myStart.getElement();
  }

  @Nullable
  public PsiElement getEnd() {
    return myEnd.getElement();
  }

  public TextRange getTextRange() {
    return getTextRange(getStart(), getEnd());
  }

  @Nullable
  public static TextRange getTextRange(@Nullable PsiElement start, @Nullable PsiElement end) {
    if (start != null && end != null) {
      return new TextRange(start.getTextRange().getStartOffset(), end.getTextRange().getEndOffset());
    }
    return null;
  }

  @Override
  protected Navigatable getNavigatable() {
    return ObjectUtils.tryCast(getStart(), Navigatable.class);
  }

  public boolean isExcluded() {
    return myExcluded;
  }

  public void setExcluded(boolean excluded) {
    myExcluded = excluded;
  }
}
