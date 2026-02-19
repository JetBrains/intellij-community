// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.refactoring.rename.inplace.InplaceRefactoring;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public interface InplaceRefactoringContinuation {

  Key<InplaceRefactoringContinuation> INPLACE_REFACTORING_CONTINUATION = Key.create("inplace.refactoring.continuation");

  static boolean hasInplaceContinuation(@NotNull Editor editor, @NotNull Object refactoringKey) {
    InplaceRefactoringContinuation continuation = editor.getUserData(INPLACE_REFACTORING_CONTINUATION);
    return continuation != null && continuation.getRefactoringKey() == refactoringKey;
  }

  /**
   * Obtains a continuation from {@code editor} and tries to resume it, if its key matches the {@code refactoringKey}.
   * If the continuation key doesn't match, or if it cannot be resumed,
   * then this method shows a message that another refactoring is in progress.
   *
   * @return `true` if there exists any continuation in the context editor, `false` otherwise
   */
  static boolean tryResumeInplaceContinuation(@NotNull Project project, @NotNull Editor editor, @NotNull Object refactoringKey) {
    InplaceRefactoringContinuation continuation = editor.getUserData(INPLACE_REFACTORING_CONTINUATION);
    if (continuation == null) {
      return false;
    }
    if (continuation.getRefactoringKey() == refactoringKey) {
      if (!continuation.resumeRefactoring(project, editor)) {
        InplaceRefactoring.unableToStartWarning(project, editor);
      }
    }
    return true;
  }

  @NotNull Object getRefactoringKey();

  /**
   * @return `true` if the refactoring was resumed and no further action is required, `false` otherwise
   */
  boolean resumeRefactoring(@NotNull Project project, @NotNull Editor editor);
}
