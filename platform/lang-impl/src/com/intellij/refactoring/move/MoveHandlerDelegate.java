// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.move;

import com.intellij.ide.CopyPasteDelegator;
import com.intellij.lang.Language;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * An extension point for "Move" refactorings.
 * <p/>
 * Delegates are processed one by one, the first delegate which agrees to handle element ({@link #canMove(PsiElement[], PsiElement, PsiReference)}) will be used, rest are ignored.
 * Natural loading order can be changed by providing attribute "order" during registration in plugin.xml.
 */
public abstract class MoveHandlerDelegate {
  public static final ExtensionPointName<MoveHandlerDelegate> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.moveHandler");

  /**
   * @param targetContainer  not-null value if drag-n-drop was used.
   * @param reference        null, if refactoring is performed on the declaration.
   * @return {@code true} if delegate can process {@code elements}.
   */
  public boolean canMove(PsiElement[] elements, final @Nullable PsiElement targetContainer, @Nullable PsiReference reference) {
    return canMove(elements, targetContainer);
  }

  /**
   * @deprecated Please overload {@link #canMove(PsiElement[], PsiElement, PsiReference)} instead.
   */
  @Deprecated
  public boolean canMove(PsiElement[] elements, final @Nullable PsiElement targetContainer) {
    return isValidTarget(targetContainer, elements);
  }

  /**
   * @return {@code true} if delegate can process {@code dataContext}.
   */
  public boolean canMove(DataContext dataContext){
    return false;
  }

  /**
   * Called from drag-n-drop to check if there is delegate to drop to this {@code targetElement}.
   * 
   * @param sources         elements which are moved.
   * @param targetElement   element under mouse.
   */
  public boolean isValidTarget(final @Nullable PsiElement targetElement, PsiElement[] sources) {
    return false;
  }

  /**
   * Performs move refactoring.
   * <p/>
   * Called in EDT without Read/Write action. Can show UI to ask user to choose target container e.g., when {@code targetContainer} is null.
   * @param project          project where refactoring is performed.
   * @param elements         elements which were selected by the user.
   * @param targetContainer  target container or {@code null} if target was not specified by drag-n-drop.
   * @param callback         callback which should be performed when refactoring is completed e.g., 
   *                         to clear some data ({@link CopyPasteDelegator.MyEditable#pasteAfterCut(DataContext, PsiElement[], PsiElement)}) 
   */
  public void doMove(final Project project, final PsiElement[] elements,
                     final @Nullable PsiElement targetContainer, final @Nullable MoveCallback callback) {
  }

  /**
   * Can replace {@code targetContainer} with more appropriate element based on information from {@code dataContext} e.g., package -> directory.
   */
  public PsiElement adjustTargetForMove(DataContext dataContext, PsiElement targetContainer) {
    return targetContainer;
  }

  /**
   * Performs some extra checks (that canMove does not).
   * May replace some elements with others which actually shall be moved (e.g. directory->package).
   */
  public PsiElement @Nullable [] adjustForMove(Project project, PsiElement[] sourceElements, PsiElement targetElement) {
    return sourceElements;
  }

  /**
   * @return true if the delegate is able to move an element.
   */
  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext,
                           final @Nullable PsiReference reference, final Editor editor) {
    return false;
  }

  /**
   * When no custom delegate can handle the full selection, default {@link MoveFilesOrDirectoriesHandler} should be used. 
   * To allow additional elements to be moved together with the selection, store them in {@code filesOrDirs} set.
   */
  public void collectFilesOrDirsFromContext(DataContext dataContext, Set<PsiElement> filesOrDirs){
  }

  /**
   * Called during drag-n-drop.
   * 
   * @return {@code true} if {@code source} is already in {@code target}.
   */
  public boolean isMoveRedundant(PsiElement source, PsiElement target) {
    return false;
  }

  /**
   * @return custom name for "Move" refactoring based on passed {@code elements} e.g., "Move Members".
   */
  public @Nullable @NlsActions.ActionText String getActionName(PsiElement @NotNull [] elements) {
    return null;
  }

  /**
   * Called from {@link #getActionName(PsiElement[])} and as optimization for common {@link #canMove(PsiElement[], PsiElement, PsiReference)}.
   * 
   * @return {@code true} if delegate supports {@code language}.
   */
  public boolean supportsLanguage(@NotNull Language language) {
    return true;
  }
}
