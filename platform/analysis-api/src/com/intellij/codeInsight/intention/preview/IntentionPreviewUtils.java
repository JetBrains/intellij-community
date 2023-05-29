// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.preview;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.ModChooseTarget;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModNavigate;
import com.intellij.modcommand.ModUpdatePsiFile;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.ApiStatus;
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
   * @param file file to get the preview copy
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
   * @param <E> exception the action throws
   * @throws E if action throws
   */
  public static <E extends Throwable> void write(@NotNull ThrowableRunnable<E> action) throws E {
    if (PREVIEW_EDITOR.get() != null) {
      action.run();
    } else {
      WriteAction.run(action);
    }
  }

  /**
   * Performs a preview-aware write-action. Execute action immediately if preview is active; wrap into write action otherwise.
   *
   * @param action action to execute
   * @param <E> exception the action throws
   * @return result of the action
   * @throws E if action throws
   */
  public static <T, E extends Throwable> T writeAndCompute(@NotNull ThrowableComputable<T, E> action) throws E {
    if (PREVIEW_EDITOR.get() != null) {
      return action.compute();
    } else {
      return WriteAction.compute(action);
    }
  }

  /**
   * Start a preview session with given editor (generatePreview call should be wrapped)
   * @param editor preview editor to use
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
  public static @Nullable Editor getPreviewEditor() {
    return PREVIEW_EDITOR.get();
  }

  /**
   * @return true if intention preview is currently being computed in this thread
   */
  public static boolean isIntentionPreviewActive() {
    return PREVIEW_EDITOR.get() != null;
  }

  /**
   * @param modCommand {@link ModCommand} to generate preview for
   * @param file current file
   * @return default preview for a given ModCommand
   */
  @ApiStatus.Experimental
  public static @NotNull IntentionPreviewInfo getModCommandPreview(@NotNull ModCommand modCommand, @NotNull PsiFile file) {
    Project project = file.getProject();
    IntentionPreviewInfo info = null;
    IntentionPreviewInfo info2 = IntentionPreviewInfo.EMPTY;
    for (ModCommand command : modCommand.unpack()) {
      if (command instanceof ModUpdatePsiFile modFile) {
        if (info != null) {
          return IntentionPreviewInfo.EMPTY;
        }
        if (file == modFile.file() || InjectedLanguageManager.getInstance(project).getTopLevelFile(file) == modFile.file()) {
          info = new IntentionPreviewInfo.Diff(modFile.oldText(), modFile.newText());
        } else {
          info = new IntentionPreviewInfo.CustomDiff(modFile.file().getFileType(), modFile.file().getName(), modFile.oldText(),
                                                     modFile.newText());
        }
      }
      else if (command instanceof ModNavigate navigate && navigate.caret() != -1) {
        PsiFile target = PsiManager.getInstance(project).findFile(navigate.file());
        if (target != null) {
          info2 = IntentionPreviewInfo.navigate(target, navigate.caret());
        }
      }
      else if (command instanceof ModChooseTarget<?> target) {
        return getChoosePreview(file, target);
      }
    }
    return info == null ? info2 : info;
  }

  private static @NotNull <T extends PsiElement> IntentionPreviewInfo getChoosePreview(@NotNull PsiFile file, @NotNull ModChooseTarget<@NotNull T> target) {
    var elements = target.elements();
    if (elements.isEmpty()) {
      return IntentionPreviewInfo.EMPTY;
    }
    return getModCommandPreview(target.nextStep().apply(elements.get(0).element()), file);
  }
}
