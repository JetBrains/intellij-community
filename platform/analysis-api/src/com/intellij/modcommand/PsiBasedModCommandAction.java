// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.modcommand;

import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * A convenient abstract class to implement {@link ModCommandAction}
 * that starts on a given {@link PsiElement}, or a {@link PsiElement} of a given type under the caret.
 * 
 * @param <E> type of the element
 */
public abstract class PsiBasedModCommandAction<E extends PsiElement> implements ModCommandAction {
  private final @Nullable SmartPsiElementPointer<E> myPointer;
  private final @Nullable Class<E> myClass;
  private final @Nullable Predicate<? super E> myFilter;

  /**
   * Constructs an instance, which is bound to a specified element
   * 
   * @param element element to start the action at.
   */
  protected PsiBasedModCommandAction(@NotNull E element) {
    myPointer = SmartPointerManager.createPointer(element);
    myClass = null;
    myFilter = null;
  }

  /**
   * Constructs an instance, which will look for an element 
   * of a specified class at the caret offset.
   * 
   * @param elementClass element class
   */
  protected PsiBasedModCommandAction(@NotNull Class<E> elementClass) {
    myPointer = null;
    myClass = elementClass;
    myFilter = null;
  }

  /**
   * Constructs an instance, which will look for an element 
   * of a specified class at the caret offset, satisfying the specified filter.
   * 
   * @param elementClass element class
   * @param filter predicate to check the elements: elements that don't satisfy will be skipped
   */
  protected PsiBasedModCommandAction(@NotNull Class<E> elementClass, @NotNull Predicate<? super E> filter) {
    myPointer = null;
    myClass = elementClass;
    myFilter = filter;
  }

  @Override
  public final @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    E element = getElement(context);
    return element == null ? null : getPresentation(context, element);
  }

  private @Nullable E getElement(@NotNull ActionContext context) {
    if (myPointer != null) {
      E element = myPointer.getElement();
      if (element != null && !BaseIntentionAction.canModify(element)) return null;
      return element;
    }
    int offset = context.offset();
    PsiFile file = context.file();
    if (!BaseIntentionAction.canModify(file)) return null;
    Class<E> cls = Objects.requireNonNull(myClass);
    if (context.element() != null && context.element().isValid()) {
      return getIfSatisfied(context.element());
    }
    PsiElement right = file.findElementAt(offset);
    PsiElement left = offset > 0 ? file.findElementAt(offset - 1) : right;
    if (left == null && right == null) return null;
    if (left == null) left = right;
    if (right == null) right = left;
    PsiElement commonParent = PsiTreeUtil.findCommonParent(left, right);
    while (left != commonParent || right != commonParent) {
      E result = getIfSatisfied(right);
      if (result != null) return result;
      result = getIfSatisfied(left);
      if (result != null) return result;
      if (left != commonParent) left = left.getParent();
      if (right != commonParent) right = right.getParent();
    }
    while(true) {
      if (commonParent == null) return null;
      if (cls.isInstance(commonParent)) return cls.cast(commonParent);
      if (commonParent instanceof PsiFile) return null;
      commonParent = commonParent.getParent();
    }
  }

  private E getIfSatisfied(PsiElement element) {
    Class<E> cls = Objects.requireNonNull(myClass);
    if (!cls.isInstance(element)) return null;
    E result = cls.cast(element);
    if (myFilter != null && !myFilter.test(result)) return null;
    return result;
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
   * @param element context element
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
   * @param element context element
   * @return a {@link ModCommand} to be executed to actually apply the action
   */
  protected abstract @NotNull ModCommand perform(@NotNull ActionContext context, @NotNull E element);

  /**
   * @param context context in which the action is executed
   * @param element context element
   * @return presentation if the action is available in the given context, and perform could be safely called;
   * null if the action is not available. By default, a simple presentation using the {@link #getFamilyName()}
   * is returned.
   */
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull E element) {
    return Presentation.of(getFamilyName());
  }
}
