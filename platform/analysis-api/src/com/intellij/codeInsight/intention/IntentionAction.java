// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Intention actions are invoked by pressing
 * Alt-Enter in the code editor at the location where an intention is available,
 * and can be enabled or disabled in the "Intentions" settings dialog.
 * <p/>
 * Implement {@link Iconable Iconable} interface to
 * change icon in intention popup menu.
 * <p/>
 * Implement {@link HighPriorityAction HighPriorityAction},
 * {@link LowPriorityAction LowPriorityAction} or {@link PriorityAction} to change ordering.
 * <p/>
 * Can be {@link com.intellij.openapi.project.DumbAware}.
 */
public interface IntentionAction extends FileModifier {

  IntentionAction[] EMPTY_ARRAY = new IntentionAction[0];

  /**
   * Returns text to be shown in the list of available actions, if this action
   * is available.
   *
   * @return the text to show in the intention popup.
   * @see #isAvailable(Project, Editor, PsiFile)
   */
  @IntentionName
  @NotNull
  String getText();

  /**
   * Returns the name of the family of intentions. It is used to externalize
   * "auto-show" state of intentions. When the user clicks on a light bulb in intention list,
   * all intentions with the same family name get enabled/disabled.
   * The name is also shown in settings tree.
   *
   * @return the intention family name.
   */
  @NotNull
  @IntentionFamilyName
  String getFamilyName();

  /**
   * Checks whether this intention is available at a caret offset in the file.
   * If this method returns true, a light bulb for this intention is shown.
   *
   * It is supposed to be fast enough to be run on EDT thread as well.
   *
   * @param project the project in which the availability is checked.
   * @param editor  the editor in which the intention will be invoked.
   * @param file    the file open in the editor.
   * @return {@code true} if the intention is available, {@code false} otherwise.
   */
  boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file);

  /**
   * Called when user invokes intention. This method is called inside command.
   * If {@link #startInWriteAction()} returns {@code true}, this method is also called
   * inside write action.
   *
   * @param project the project in which the intention is invoked.
   * @param editor  the editor in which the intention is invoked.
   * @param file    the file open in the editor.
   */
  void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException;

  /**
   * Indicate whether this action should be invoked inside write action.
   * Should return {@code false} if, e.g., a modal dialog is shown inside the action.
     * If false is returned the action itself is responsible for starting write action
   * when needed, by calling {@link Application#runWriteAction(Runnable)}.
   *
   * @return {@code true} if the intention requires a write action, {@code false} otherwise.
   */
  @Override
  boolean startInWriteAction();

  /**
   * Invokes this action on non-physical file to display the preview. This method is called outside write action
   * in background thread, even if {@link #startInWriteAction()} returns true. It's not allowed to modify
   * any physical PSI or spawn any actions in other threads within this method. This method may behave differently than
   * {@link #invoke(Project, Editor, PsiFile)} method. In particular, changes in other files or user interactions
   * like renaming the created variable should not be performed by this method.
   * <p>
   * Default implementation calls {@link #getFileModifierForPreview(PsiFile)} and {@link #invoke(Project, Editor, PsiFile)}
   * on the result. This might fail if the original intention action is not prepared for preview. In this case,
   * overriding {@code getFileModifierForPreview} or {@code invokeForPreview} is desired.
   *
   * @param project current project
   * @param editor editor. Could be a simplified headless Editor implementation that lacks some features.
   * @param file non-physical file to apply
   * @return true if the action was applied successfully to the non-physical file.
   * @deprecated do not call or override this method: this API will be changed.
   */
  @Deprecated(forRemoval = true)
  default boolean invokeForPreview(@NotNull Project project, Editor editor, PsiFile file) {
    if (!startInWriteAction()) return false;
    var copy = ObjectUtils.tryCast(getFileModifierForPreview(file), IntentionAction.class);
    if (copy == null || copy.getElementToMakeWritable(file) != file) return false;
    copy.invoke(project, editor, file);
    return true;
  }

  /**
   * Generates intention preview for this action. This method is called outside write action
   * in background thread, even if {@link #startInWriteAction()} returns true. It's not allowed to modify
   * any physical PSI or spawn any actions in other threads within this method.
   * <p>
   * There are several possibilities to make the preview:
   * <ul>
   *   <li>Apply changes to the supplied {@code file}, then return {@link IntentionPreviewInfo#DIFF}. The supplied file is
   *   a non-physical copy of the original file.</li>
   *   <li>Return {@link IntentionPreviewInfo.Html} object to display custom HTML</li>
   *   <li>Return {@link IntentionPreviewInfo#EMPTY} to generate no preview at all</li>
   * </ul>
   * <p>
   * Default implementation calls {@link #getFileModifierForPreview(PsiFile)} and {@link #invoke(Project, Editor, PsiFile)}
   * on the result. This might fail if the original intention action is not prepared for preview. In this case,
   * overriding {@code getFileModifierForPreview} or {@code generatePreview} is desired.
   *
   * @param project current project
   * @param editor editor. Could be a simplified headless Editor implementation that lacks some features.
   * @param file non-physical file to apply
   * @return an object that describes the action preview to display
   */
  default @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    if (!startInWriteAction()) return IntentionPreviewInfo.EMPTY;
    var copy = ObjectUtils.tryCast(getFileModifierForPreview(file), IntentionAction.class);
    if (copy == null || copy.getElementToMakeWritable(file) != file) return IntentionPreviewInfo.FALLBACK_DIFF;
    copy.invoke(project, editor, file);
    return IntentionPreviewInfo.DIFF;
  }
}
