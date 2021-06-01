// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.refactoring.move;

import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;


public abstract class MoveHandlerDelegate {
  public static final ExtensionPointName<MoveHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.moveHandler");

  public boolean canMove(PsiElement[] elements, @Nullable final PsiElement targetContainer, @Nullable PsiReference reference) {
    return canMove(elements, targetContainer);
  }

  /**
   * @deprecated Please overload {@link #canMove(PsiElement[], PsiElement, PsiReference)} instead.
   */
  @Deprecated
  public boolean canMove(PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    return isValidTarget(targetContainer, elements);
  }

  public boolean canMove(DataContext dataContext){
    return false;
  }

  public boolean isValidTarget(@Nullable final PsiElement targetElement, PsiElement[] sources) {
    return false;
  }

  public void doMove(final Project project, final PsiElement[] elements,
                     @Nullable final PsiElement targetContainer, @Nullable final MoveCallback callback) {
  }

  public PsiElement adjustTargetForMove(DataContext dataContext, PsiElement targetContainer) {
    return targetContainer;
  }

  public PsiElement @Nullable [] adjustForMove(Project project, PsiElement[] sourceElements, PsiElement targetElement) {
    return sourceElements;
  }

  /**
   * @return true if the delegate is able to move an element
   */
  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext,
                           @Nullable final PsiReference reference, final Editor editor) {
    return false;
  }

  public void collectFilesOrDirsFromContext(DataContext dataContext, Set<PsiElement> filesOrDirs){
  }

  public boolean isMoveRedundant(PsiElement source, PsiElement target) {
    return false;
  }

  @Nullable
  @NlsActions.ActionText
  public String getActionName(PsiElement @NotNull [] elements) {
    return null;
  }

  public boolean supportsLanguage(@NotNull Language language) {
    return true;
  }
}
