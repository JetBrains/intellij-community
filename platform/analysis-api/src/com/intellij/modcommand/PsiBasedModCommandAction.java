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
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
    myPointer = SmartPointerManager.createPointer(element);
    myClass = null;
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
  }

  @Override
  public final @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    E element = getElement(context);
    return element == null ? null : getPresentation(context, element);
  }

  @Nullable
  private E getElement(@NotNull ActionContext context) {
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
      return ObjectUtils.tryCast(context.element(), cls);
    }
    PsiElement element = file.findElementAt(offset);
    E target = PsiTreeUtil.getNonStrictParentOfType(element, cls);
    if (target == null && offset > 0) {
      element = file.findElementAt(offset - 1);
      target = PsiTreeUtil.getNonStrictParentOfType(element, cls);
    }
    return target;
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
    return IntentionPreviewUtils.getModCommandPreview(command, context.file());
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
