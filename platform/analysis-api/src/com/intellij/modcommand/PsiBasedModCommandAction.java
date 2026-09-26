// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A convenient abstract class to implement {@link ModCommandAction}
 * that starts on a given {@link PsiElement}, or a {@link PsiElement} of a given type under the caret.
 *
 * @param <E> type of the element
 */
public abstract class PsiBasedModCommandAction<E extends PsiElement> implements ModCommandAction {
  private final @Nullable SmartPsiElementPointer<E> myPointer;
  private final @Nullable Class<E> myClass;

  /**
   * Constructs an instance, which is bound to a specified element
   *
   * @param element element to start the action at.
   */
  protected PsiBasedModCommandAction(@NotNull E element) {
    this(element, null);
  }

  /**
   * Constructs an instance, which will look for an element
   * of a specified class at the caret offset.
   *
   * @param elementClass element class
   */
  protected PsiBasedModCommandAction(@NotNull Class<E> elementClass) {
    this(null, elementClass);
  }

  // todo to be decomposed into 2 base classes
  @ApiStatus.Internal
  protected PsiBasedModCommandAction(@Nullable E element,
                                     @Nullable Class<E> elementClass) {
    assert element != null || elementClass != null;
    myPointer = element != null ? SmartPointerManager.createPointer(element) : null;
    myClass = elementClass;
  }

  @Override
  public final @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    E element = getElement(context);
    if (element == null) return null;
    Presentation presentation = getPresentation(context, element);
    return addApplicableRange(presentation, context, element);
  }

  private @Nullable Presentation addApplicableRange(@Nullable Presentation presentation,
                                                    @NotNull ActionContext context,
                                                    @NotNull E element) {
    if (presentation == null) return null;
    //applicable range is used only inside the file where the action is called,
    //otherwise it can cause ast loading
    if (context.file() != element.getContainingFile()) {
      return presentation;
    }
    List<Presentation.HighlightRange> ranges = new ArrayList<>(presentation.rangesToHighlight());
    ranges.add(new Presentation.HighlightRange(element.getTextRange(), Presentation.HighlightingKind.APPLICABLE_TO_RANGE));
    return presentation.withHighlighting(ranges.toArray(Presentation.HighlightRange[]::new));
  }

  private @Nullable E getElement(@NotNull ActionContext context) {
    if (myPointer != null) {
      E element = myPointer.getElement();
      if (element == null) return null;
      PsiFile file = element.getContainingFile();
      // File could be null e.g., for PsiPackage element
      if (file != null && !isFileAllowed(file)) return null;
      return element;
    }
    int offset = context.offset();
    PsiFile file = context.file();
    if (!isFileAllowed(file)) return null;
    if (context.element() != null && context.element().isValid()) {
      return getIfSatisfied(context.element(), context);
    }
    PsiElement right = file.findElementAt(offset);
    PsiElement left = offset > 0 ? file.findElementAt(offset - 1) : right;
    if (left == null && right == null) return null;
    if (left == null) left = right;
    if (right == null) right = left;
    PsiElement commonParent = PsiTreeUtil.findCommonParent(left, right);

    if (left != right) {
      while (right != commonParent) {
        E result = getIfSatisfied(right, context);
        if (result != null) return result;
        right = right.getParent();
      }
    }

    while (left != commonParent) {
      E result = getIfSatisfied(left, context);
      if (result != null) return result;
      left = left.getParent();
    }

    while (true) {
      if (commonParent == null) return null;
      E satisfied = getIfSatisfied(commonParent, context);
      if (satisfied != null) return satisfied;
      if (stopSearchAt(commonParent, context) || commonParent instanceof PsiFile) return null;
      commonParent = commonParent.getParent();
    }
  }

  /**
   * Check if the action is allowed in the supplied file. By default, it checks whether the file could be modified.
   * Override if you want to perform a custom check.
   * 
   * @param file file to check
   * @return true if the action is allowed inside the specified file; false otherwise
   */
  protected boolean isFileAllowed(@NotNull PsiFile file) {
    return BaseIntentionAction.canModify(file);
  }

  private E getIfSatisfied(@NotNull PsiElement element, @NotNull ActionContext context) {
    Class<E> cls = Objects.requireNonNull(myClass);
    if (!cls.isInstance(element)) return null;
    E e = cls.cast(element);
    return isElementApplicable(e, context) ? e : null;
  }

  /**
   * @param element physical element to test
   * @param context context
   * @return true if no parent elements should be checked for applicability. By default, returns false,
   * so we will search for the applicable element until {@link PsiFile} element is reached.
   */
  protected boolean stopSearchAt(@NotNull PsiElement element, @NotNull ActionContext context) {
    return false;
  }

  /**
   * @param element physical element to test
   * @param context context
   * @return true, if the supplied element is the one we want to apply the action on. Used when
   * searching for the appropriate element. By default, returns true always, meaning that the first found element
   * of type E is applicable.
   */
  protected boolean isElementApplicable(@NotNull E element, @NotNull ActionContext context) {
    return true;
  }

  @Override
  public final @NotNull ModCommand perform(@NotNull ActionContext context) {
    E element = getElement(context);
    if (element == null) return ModNothing.NOTHING;
    return perform(context, element);
  }

  @Override
  public final @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    E element = getElement(context);
    if (element == null) return IntentionPreviewInfo.EMPTY;
    return generatePreview(context, element);
  }

  /**
   * @param context context where the action is executed
   * @param element context element (physical)
   * @return preview for this action. By default, {@link #perform(ActionContext, PsiElement)} is launched,
   * and preview is based on its result.
   */
  protected @NotNull IntentionPreviewInfo generatePreview(ActionContext context, E element) {
    ModCommand command = perform(context, element);
    return IntentionPreviewUtils.getModCommandPreview(command, context);
  }

  /**
   * Computes a command to be executed to actually perform the action.
   * Called in a background read-action. Called after {@link #getPresentation(ActionContext)} returns non-null presentation.
   *
   * @param context context in which the action is executed
   * @param element context element (physical)
   * @return a {@link ModCommand} to be executed to actually apply the action
   */
  protected abstract @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull E element);

  /**
   * @param context context in which the action is executed
   * @param element context element (physical)
   * @return presentation if the action is available in the given context, and perform could be safely called;
   * null if the action is not available. By default, a simple presentation using the {@link #getFamilyName()}
   * is returned.
   */
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull E element) {
    return Presentation.of(getFamilyName());
  }
}
