// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.preview;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utils to support intention preview feature
 */
public final class IntentionPreviewUtils {
  private static final Key<PsiFile> PREVIEW_MARKER = Key.create("PREVIEW_MARKER");
  private static final ThreadLocal<Editor> PREVIEW_EDITOR = new ThreadLocal<>();

  /**
   * This method is a part of the internal implementation of an intention preview mechanism.
   * It's not intended to be called in the client code.
   *
   * @param file file to get the preview copy
   * @return a preview copy of the file
   */
  @ApiStatus.Internal
  public static @NotNull PsiFile obtainCopyForPreview(@NotNull PsiFile file) {
    return obtainCopyForPreview(file, file);
  }

  /**
   * This method is a part of the internal implementation of an intention preview mechanism.
   * It's not intended to be called in the client code.
   *
   * @param file         file to get the preview copy
   * @param originalFile original file. May differ from file if we are inside the injection
   * @return a preview copy of the file
   */
  @ApiStatus.Internal
  public static @NotNull PsiFile obtainCopyForPreview(@NotNull PsiFile file, @NotNull PsiFile originalFile) {
    PsiFile copy = (PsiFile)file.copy();
    copy.putUserData(PREVIEW_MARKER, originalFile);
    return copy;
  }

  /**
   * @param element element to check
   * @return true if a given element is a copy created for preview
   */
  public static boolean isPreviewElement(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    return file != null && file.getUserData(PREVIEW_MARKER) != null;
  }

  /**
   * @param previewFile file from preview session (e.g., passed to {@link IntentionAction#generatePreview})
   * @return original physical file, or null if this file is not the preview copy
   */
  public static @Nullable PsiFile getOriginalFile(@NotNull PsiFile previewFile) {
    return previewFile.getUserData(PREVIEW_MARKER);
  }

  /**
   * Prepares an element for writing, taking into account that it could be a preview element
   *
   * @param element element to prepare
   * @return true if an element can be written
   */
  public static boolean prepareElementForWrite(@NotNull PsiElement element) {
    if (isPreviewElement(element)) return true;
    return FileModificationService.getInstance().preparePsiElementForWrite(element);
  }

  /**
   * Performs a preview-aware write-action. Execute action immediately if preview is active; wrap into write action otherwise.
   *
   * @param action action to execute
   * @param <E>    exception the action throws
   * @throws E if action throws
   */
  public static <E extends Throwable> void write(@NotNull ThrowableRunnable<E> action) throws E {
    if (PREVIEW_EDITOR.get() != null) {
      action.run();
    }
    else {
      WriteAction.run(action);
    }
  }

  /**
   * Performs a preview-aware write-action. Execute action immediately if preview is active; wrap into write action otherwise.
   *
   * @param action action to execute
   * @param <E>    exception the action throws
   * @return result of the action
   * @throws E if action throws
   */
  public static <T, E extends Throwable> T writeAndCompute(@NotNull ThrowableComputable<T, E> action) throws E {
    if (PREVIEW_EDITOR.get() != null) {
      return action.compute();
    }
    else {
      return WriteAction.compute(action);
    }
  }

  /**
   * Start a preview session with given editor (generatePreview call should be wrapped)
   *
   * @param editor   preview editor to use
   * @param runnable action to execute
   */
  @ApiStatus.Internal
  public static void previewSession(@NotNull Editor editor, @NotNull Runnable runnable) {
    PREVIEW_EDITOR.set(editor);
    try {
      runnable.run();
    }
    finally {
      PREVIEW_EDITOR.remove();
    }
  }

  /**
   * @return current imaginary editor used for preview; null if we are not in preview session
   */
  @Contract(pure = true)
  public static @Nullable Editor getPreviewEditor() {
    return PREVIEW_EDITOR.get();
  }

  /**
   * @return true if intention preview is currently being computed in this thread
   */
  @Contract(pure = true)
  public static boolean isIntentionPreviewActive() {
    return PREVIEW_EDITOR.get() != null;
  }

  /**
   * @param modCommand {@link ModCommand} to generate preview for
   * @param context    context in which the action is about to be executed
   * @return default preview for a given ModCommand
   */
  @ApiStatus.Experimental
  @Contract(pure = true)
  public static @NotNull IntentionPreviewInfo getModCommandPreview(@NotNull ModCommand modCommand, @NotNull ActionContext context) {
    return ModCommandExecutor.getInstance().getPreview(modCommand, context);
  }

  /**
   * Tries to convert an element from an original file to the same element in the preview file.
   * This method should be used only before any modification to the preview file is made.
   * <p>
   * If {@code file} is an original file or {@code element} is already a preview element, then {@code element} is returned as is.
   * Otherwise an attempt to convert the element to a preview element is made. For that to succeed, {@code element} must be from
   * the original file of the preview {@code file}.
   *
   */
  public static <T extends PsiElement> @Nullable T toPreviewElementIfPreviewFile(@NotNull PsiFile file, @NotNull T element) {
    if (!isPreviewElement(file) || isPreviewElement(element)) return element;
    if (getOriginalFile(file) != element.getContainingFile()) return null;
    var range = element.getTextRange();
    //noinspection unchecked
    return (T)PsiTreeUtil.findElementOfClassAtRange(file, range.getStartOffset(), range.getEndOffset(),
                                                 element.getClass());
  }
}
