// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.codeInsight.hint.QuestionAction;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.ui.HintListener;
import com.intellij.ui.LightweightHint;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EventObject;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Remembers the import hints which the user hid with the Escape key.
 * The daemon alone forgets the Escape key on the next caret move, so it shows the hint again.
 * <p>
 * The hint keeps the decision in {@link LightweightHint#isDismissedByEscape()}.
 * Every hide route sets that property, which includes split mode, where the client owns the key press.
 * <p>
 * A hint is identified by the reference it belongs to, and by the name of that reference.
 * The reference keeps the decision on one usage, so a different usage of the same name still gets a hint.
 * The name cancels the decision if the user makes the reference into a different name.
 */
@ApiStatus.Internal
public final class ImportHintDismissalTracker {
  private static final Key<Set<DismissedHint>> DISMISSED_HINTS = Key.create("import.hint.dismissed.hints");

  private record DismissedHint(@NotNull SmartPsiElementPointer<?> pointer, @NotNull String referenceName) {
  }

  private ImportHintDismissalTracker() {
  }

  /**
   * @param reference     the unresolved reference the hint belongs to
   * @param referenceName the name of that reference
   * @return true if the user hid the import hint for this reference
   */
  @RequiresReadLock
  public static boolean isDismissed(@NotNull Editor editor, @Nullable PsiElement reference, @Nullable String referenceName) {
    if (reference == null || referenceName == null) return false;
    Set<DismissedHint> dismissedHints = editor.getUserData(DISMISSED_HINTS);
    if (dismissedHints == null) return false;
    return ContainerUtil.exists(dismissedHints, hint -> hint.referenceName().equals(referenceName)
                                                        && reference.equals(hint.pointer().getElement()));
  }

  /**
   * Shows the import hint. Remembers the decision if the user hides the hint with the Escape key.
   *
   * @param reference     the unresolved reference the hint belongs to, or null to show a hint which this tracker ignores
   * @param referenceName the name of that reference, or null to show a hint which this tracker ignores
   */
  @RequiresEdt
  public static void showHint(@NotNull Editor editor,
                              @NotNull @NlsContexts.HintText String hintText,
                              int startOffset,
                              int endOffset,
                              @NotNull QuestionAction action,
                              @Nullable PsiElement reference,
                              @Nullable String referenceName) {
    LightweightHint hint = new LightweightHint(HintUtil.createQuestionLabel(hintText));
    if (reference != null && referenceName != null) {
      hint.addHintListener(new HintListener() {
        @Override
        public void hintHidden(@NotNull EventObject event) {
          if (!hint.isDismissedByEscape()) return;
          hint.removeHintListener(this);
          ReadAction.nonBlocking((Callable<Void>)() -> {
            if (reference.isValid()) {
              dismiss(editor, reference, referenceName);
            }
            return null;
          }).executeSynchronously();
        }
      });
    }
    HintManagerImpl.getInstanceImpl().showQuestionHint(editor, startOffset, endOffset, hint, action, HintManager.ABOVE);
  }

  @RequiresReadLock
  private static void dismiss(@NotNull Editor editor, @NotNull PsiElement reference, @NotNull String referenceName) {
    Set<DismissedHint> dismissedHints = editor.getUserData(DISMISSED_HINTS);
    if (dismissedHints == null) {
      dismissedHints = ContainerUtil.newConcurrentSet();
      editor.putUserData(DISMISSED_HINTS, dismissedHints);
    }
    dismissedHints.removeIf(hint -> hint.pointer().getElement() == null);
    dismissedHints.add(new DismissedHint(SmartPointerManager.createPointer(reference), referenceName));
  }
}
