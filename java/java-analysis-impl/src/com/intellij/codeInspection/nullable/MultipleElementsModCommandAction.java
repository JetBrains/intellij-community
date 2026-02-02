// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.nullable;

import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A ModCommand action that performs multiple PsiElement changes.
 * Each change is passed a a {@link Change} object
 * that contains PsiElement to be modified (target) and the operation to performed.
 * Internally this action uses {@link SmartPsiElementPointer}s to track the target elements.
 */
@NotNullByDefault
class MultipleElementsModCommandAction implements ModCommandAction {
  @IntentionName
  private final String familyName;
  private final List<SmartChange<? extends PsiElement>> changes;

  /**
   * Creates a new action that performs multiple PsiElement changes.
   *
   * @param familyName the name of the action family to be displayed in the UI
   * @param changes    list of changes to be performed on PsiElements
   */
  MultipleElementsModCommandAction(@IntentionName String familyName, List<Change<? extends PsiElement>> changes) {
    this.changes = ContainerUtil.map(changes, change -> change.toSmartChange());
    this.familyName = familyName;
  }

  @Override
  public String getFamilyName() {
    return familyName;
  }

  @Override
  public Presentation getPresentation(ActionContext context) {
    return Presentation.of(familyName);
  }

  @Override
  public ModCommand perform(ActionContext context) {
    return ModCommand.psiUpdate(context, updater -> applyOperations(changes, updater));
  }

  private static <T extends PsiElement> void applyOperations(List<SmartChange<? extends T>> smartChanges, ModPsiUpdater updater) {
    List<? extends Change<? extends T>> changes = convertSmartChangesToChanges(updater, smartChanges);
    if (changes == null) return;
    for (Change<? extends T> change : changes) {
      change.invokeOperation(updater);
    }
  }

  private static <T extends PsiElement> @Nullable List<? extends Change<? extends T>> convertSmartChangesToChanges(ModPsiUpdater updater,
                                                                                                                   List<SmartChange<? extends T>> smartChanges) {
    List<Change<? extends T>> changes = new ArrayList<>();
    for (SmartChange<? extends T> smartChange : smartChanges) {
      Change<? extends T> change = smartChange.toChange(updater);
      if (change == null) return null;
      changes.add(change);
    }
    return changes;
  }

  /**
   * Represents a change operation to be performed on a PsiElement.
   *
   * @param target    the PsiElement to be modified
   * @param operation the operation to be performed on the target element
   * @param <T>       the type of PsiElement
   */
  public record Change<T extends PsiElement>(T target, BiConsumer<ModPsiUpdater, T> operation) {
    SmartChange<T> toSmartChange() {
      return new SmartChange<>(SmartPointerManager.createPointer(target), operation);
    }

    public void invokeOperation(ModPsiUpdater updater) {
      operation().accept(updater, target());
    }
  }

  /**
   * Represents a change operation to be performed on a PsiElement tracked by SmartPsiElementPointer.
   *
   * @param target    smart pointer to the PsiElement to be modified
   * @param operation the operation to be performed on the target element
   * @param <T>       the type of PsiElement
   */
  private record SmartChange<T extends PsiElement>(SmartPsiElementPointer<T> target, BiConsumer<ModPsiUpdater, T> operation) {
    private @Nullable Change<T> toChange(ModPsiUpdater updater) {
      T writable = updater.getWritable(target.getElement());
      if (writable == null) return null;
      return new Change<>(writable, operation);
    }
  }
}
