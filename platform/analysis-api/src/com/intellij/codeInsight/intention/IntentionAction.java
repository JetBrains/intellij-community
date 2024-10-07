// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Intention actions are context-specific actions related to the caret position in the editor.
 * Upon pressing <kbd>Alt+Enter</kbd>, a list of possible intention actions is shown in a popup.
 * <p>
 * Individual intention actions can be enabled or disabled in the <em>Settings | Editor | Intentions</em> dialog.
 * <p>
 * To change the icon in the intention popup menu, implement {@link Iconable}.
 * <p>
 * To change the ordering, implement {@link HighPriorityAction},
 * {@link LowPriorityAction} or {@link PriorityAction}.
 * <p>
 * Can be marked {@link com.intellij.openapi.project.DumbAware}.
 * <p>
 * See {@link CustomizableIntentionAction} for further customization options.
 */
public interface IntentionAction extends FileModifier, CommonIntentionAction, PossiblyDumbAware {

  IntentionAction[] EMPTY_ARRAY = new IntentionAction[0];

  /**
   * Returns the text to be shown in the list of available actions in the intentions popup menu.
   * The action is only shown if it is available.
   *
   * @see #isAvailable(Project, Editor, PsiFile)
   */
  @IntentionName
  @NotNull
  String getText();

  /**
   * Checks whether this intention is available at the caret position in the file.
   * If this method returns true, a light bulb for this intention is shown.
   * <p>
   * It is supposed to be fast enough to be run on the EDT thread as well.
   *
   * @param project the project in which the availability is checked.
   * @param editor  the editor in which the intention will be invoked.
   * @param file    the file open in the editor.
   * @return {@code true} if the intention is available, {@code false} otherwise.
   */
  boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file);

  /**
   * Called when the user has selected the intention in order to invoke it.
   * This method is called inside a command.
   * If {@link #startInWriteAction()} returns {@code true},
   * this method is also called inside a write action.
   *
   * @param project the project in which the intention is invoked.
   * @param editor  the editor in which the intention is invoked.
   * @param file    the file open in the editor.
   */
  void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException;

  /**
   * Indicate whether this action should be invoked inside a write action.
   * Should return {@code false} if, e.g., a modal dialog is shown inside the action.
   * If false is returned, the action itself is responsible for starting the write action when needed,
   * by calling {@link WriteAction#run(ThrowableRunnable)}.
   *
   * @return {@code true} if the intention requires a write action, {@code false} otherwise.
   */
  @Override
  boolean startInWriteAction();

  /**
   * Generates the intention preview for this action.
   * This method is called outside a write action in a background thread,
   * even if {@link #startInWriteAction()} returns true.
   * It must not modify any physical PSI or spawn any actions in other threads within this method.
   * <p>
   * There are several possibilities to make the preview:
   * <ul>
   *   <li>Apply changes to the supplied {@code file}, then return {@link IntentionPreviewInfo#DIFF}. The supplied file is
   *   a non-physical copy of the original file.</li>
   *   <li>Return an {@link IntentionPreviewInfo.Html} object to display custom HTML</li>
   *   <li>Return {@link IntentionPreviewInfo#EMPTY} to generate no preview at all</li>
   * </ul>
   * <p>
   * The default implementation calls {@link #getFileModifierForPreview(PsiFile)}
   * and {@link #invoke(Project, Editor, PsiFile)} on the result.
   * This may fail if the original intention action is not prepared for preview.
   * In this case, overriding {@code getFileModifierForPreview} or {@code generatePreview} is desired.
   *
   * @param project the current project
   * @param editor  the editor where a file copy is opened.
   *                Could be a simplified headless Editor implementation that lacks some features.
   * @param file    a non-physical file to apply, which is a copy of the file that contains the element returned from
   *                {@link #getElementToMakeWritable(PsiFile)}, or a copy of the current file if that method returns null
   * @return an object that describes the action preview to display
   */
  default @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    IntentionAction copy = ObjectUtils.tryCast(getFileModifierForPreview(file), IntentionAction.class);
    if (copy == null) return IntentionPreviewInfo.FALLBACK_DIFF;
    PsiElement writable = copy.getElementToMakeWritable(file);
    if (writable == null || writable.getContainingFile() != file) return IntentionPreviewInfo.FALLBACK_DIFF;
    copy.invoke(project, editor, file);
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  default @NotNull IntentionAction asIntention() {
    return this;
  }

  @Override
  @Nullable
  default ModCommandAction asModCommandAction() {
    if (this instanceof IntentionActionDelegate delegate) {
      return delegate.getDelegate().asModCommandAction();
    }
    return null;
  }
}
