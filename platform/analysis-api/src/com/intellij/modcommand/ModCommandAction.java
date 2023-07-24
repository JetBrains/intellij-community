// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * Intention action replacement that operates on {@link ModCommand}.
 */
public interface ModCommandAction extends CommonIntentionAction {
  /**
   * @param context context in which the action is executed
   * @return presentation if the action is available in the given context, and perform could be safely called;
   * null if the action is not available
   */
  @Contract(pure = true)
  @Nullable Presentation getPresentation(@NotNull ActionContext context);
  
  /**
   * Computes a command to be executed to actually perform the action. 
   * Called in a background read-action. Called after {@link #getPresentation(ActionContext)} returns non-null presentation.
   * 
   * @param context context in which the action is executed
   * @return a {@link ModCommand} to be executed to actually apply the action
   */
  @Contract(pure = true)
  @NotNull ModCommand perform(@NotNull ActionContext context);

  /**
   * Computes a preview for this action in the particular context.
   * Default implementation derives the preview from resulting {@link ModCommand}.
   * In many cases, it might be enough.
   * 
   * @param context context in which the action is executed. Unlike {@link IntentionAction#generatePreview(Project, Editor, PsiFile)},
   *                the context points to the physical file, no copy is done in advance.
   * @return preview for the action
   */
  @Contract(pure = true)
  default @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    ModCommand command = perform(context);
    return IntentionPreviewUtils.getModCommandPreview(command, context);
  }

  /**
   * Returns a new {@link ModCommandAction} with a modified presentation.
   *
   * @param presentationModifier a {@link UnaryOperator} that modifies the presentation of the action
   * @return a new {@link ModCommandAction} with the modified presentation
   */
  default @NotNull ModCommandAction withPresentation(@NotNull UnaryOperator<Presentation> presentationModifier) {
    return new ModCommandActionPresentationDelegate(this, presentationModifier);
  }

  /**
   * @return this action adapted to {@link IntentionAction} interface
   */
  @Override
  @Contract(pure = true)
  default @NotNull IntentionAction asIntention() {
    return ModCommandService.getInstance().wrap(this);
  }

  @Override
  @NotNull
  default ModCommandAction asModCommandAction() {
    return this;
  }

  /**
   * Context in which the action is invoked
   * 
   * @param project current project
   * @param file current file
   * @param offset caret offset within the file
   * @param selection selection
   * @param element context PsiElement
   */
  record ActionContext(
    @NotNull Project project,
    @NotNull PsiFile file,
    int offset,
    @NotNull TextRange selection,
    @Nullable PsiElement element
  ) {
    /**
     * @param file file copy
     * @return new context, which is bound to the file copy, rather than the original file
     */
    public @NotNull ActionContext withFile(@NotNull PsiFile file) {
      return new ActionContext(project, file, offset, selection, element);
    }

    /**
     * @param element element
     * @return new context, which is bound to the specified element
     * @see #element() 
     */
    public ActionContext withElement(@NotNull PsiElement element) {
      return new ActionContext(project, file, offset, selection, element);
    }

    /**
     * @param offset new offset
     * @return new context, which is bound to the specified offset
     * @see #offset() 
     */
    public ActionContext withOffset(int offset) {
      return new ActionContext(project, file, offset, selection, element);
    }

    /**
     * @return a context leaf element, if available
     */
    public @Nullable PsiElement findLeaf() {
      return file.findElementAt(offset);
    }

    /**
     * @return a context leaf element left to caret, if available
     */
    public @Nullable PsiElement findLeafOnTheLeft() {
      return offset == 0 ? null : file.findElementAt(offset - 1);
    }

    /**
     * @param editor editor the action is invoked in
     * @param file file the action is invoked on
     * @return ActionContext
     */
    public static @NotNull ActionContext from(@Nullable Editor editor, @NotNull PsiFile file) {
      if (editor == null) {
        return new ActionContext(file.getProject(), file, 0, TextRange.from(0, 0), null);
      }
      SelectionModel model = editor.getSelectionModel();
      return new ActionContext(file.getProject(), file, editor.getCaretModel().getOffset(),
                                                TextRange.create(model.getSelectionStart(), model.getSelectionEnd()), null);
    }

    /**
     * @param descriptor problem descriptor to create an ActionContext from
     * @return ActionContext. The caret position is set to the beginning of highlighting, 
     * and selection is set to the highlighting range. 
     */
    public static @NotNull ActionContext from(@NotNull ProblemDescriptor descriptor) {
      PsiElement startElement = descriptor.getStartElement();
      PsiFile file = startElement.getContainingFile();
      PsiElement psiElement = descriptor.getPsiElement();
      TextRange range = descriptor.getTextRangeInElement();
      if (range != null) {
        range = range.shiftRight(psiElement.getTextRange().getStartOffset());
      } else {
        range = psiElement.getTextRange();
      }
      return new ActionContext(file.getProject(), file, range.getStartOffset(), range, startElement);
    }
  }

  record FixAllOption(
    @NotNull @IntentionName String name,
    @NotNull Predicate<@NotNull ModCommandAction> belongsToMyFamily
  ) {}

  /**
   * Represents a tuple of a TextRange and TextAttributesKey used for highlighting a specific range of text.
   *
   * @param range The TextRange to be highlighted.
   * @param highlightKey The TextAttributesKey to be used for highlighting the range.
   */
  record HighlightRange(
    @NotNull TextRange range,
    @NotNull TextAttributesKey highlightKey
  ) {}

  /**
   * Action presentation
   * 
   * @param name localized name of the action to be displayed in UI
   * @param priority priority to sort the action among other actions
   * @param icon icon to be displayed next to the name
   */
  record Presentation(
    @NotNull @IntentionName String name,
    @NotNull PriorityAction.Priority priority,
    @NotNull List<HighlightRange> rangesToHighlight,
    @Nullable Icon icon,
    @Nullable FixAllOption fixAllOption
  ) {
    /**
     * @param priority wanted priority of the action
     * @return new presentation with updated priority
     */
    public @NotNull Presentation withPriority(@NotNull PriorityAction.Priority priority) {
      return new Presentation(name, priority, rangesToHighlight, icon, fixAllOption);
    }

    /**
     * @param icon wanted icon of the action (null for default or absent icon)
     * @return new presentation with updated icon
     */
    public @NotNull Presentation withIcon(@Nullable Icon icon) {
      return new Presentation(name, priority, rangesToHighlight, icon, fixAllOption);
    }

    /**
     * @param thisAction the action the presentation is created for
     * @return a presentation for an action that has a standard "Fix all" option 
     * to fix all the issues like this in the file. Inapplicable to intention quick-fixes.
     */
    public @NotNull Presentation withFixAllOption(@NotNull ModCommandAction thisAction) {
      FixAllOption fixAllOption = new FixAllOption(
        AnalysisBundle.message("intention.name.apply.all.fixes.in.file", thisAction.getFamilyName()),
        action -> action.getClass().equals(thisAction.getClass()));
      return new Presentation(name, priority, rangesToHighlight, icon, fixAllOption);
    }

    /**
     * @param thisAction        the action the presentation is created for
     * @param belongsToMyFamily a predicate that checks if another action belongs to this action family,
     *                          and should be applied together with this action
     * @return a presentation for an action that has a standard "Fix all" option
     * to fix all the issues like this in the file. Inapplicable to intention quick-fixes.
     */
    public @NotNull Presentation withFixAllOption(@NotNull ModCommandAction thisAction,
                                                  @NotNull Predicate<@NotNull ModCommandAction> belongsToMyFamily) {
      FixAllOption fixAllOption = new FixAllOption(
        AnalysisBundle.message("intention.name.apply.all.fixes.in.file", thisAction.getFamilyName()),
        belongsToMyFamily);
      return new Presentation(name, priority, rangesToHighlight, icon, fixAllOption);
    }

    /**
     * @param ranges the ranges to highlight in the current file
     * @return a presentation that highlights the specified ranges
     */
    public @NotNull Presentation withHighlighting(@NotNull HighlightRange @NotNull ... ranges) {
      return new Presentation(name, priority, List.of(ranges), icon, fixAllOption);
    }
    
    public @NotNull Presentation withHighlighting(@NotNull TextRange @NotNull ... ranges) {
      List<HighlightRange> highlightRanges = ContainerUtil.map(ranges, r -> new HighlightRange(r, EditorColors.SEARCH_RESULT_ATTRIBUTES));
      return new Presentation(name, priority, highlightRanges, icon, fixAllOption);
    }

    /**
     * @param name localized name of the action
     * @return simple presentation with NORMAL priority and no icon
     */
    public static @NotNull Presentation of(@NotNull @IntentionName String name) {
      return new Presentation(name, PriorityAction.Priority.NORMAL, List.of(), null, null);
    }
  }
}
