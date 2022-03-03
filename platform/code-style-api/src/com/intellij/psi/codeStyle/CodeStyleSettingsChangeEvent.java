// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.codeStyle;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

/**
 * Generated when the current code style settings change either for the entire project or for a specific PSI file.
 */
public class CodeStyleSettingsChangeEvent {
  private @Nullable final PsiFile myPsiFile;

  @ApiStatus.Internal
  public CodeStyleSettingsChangeEvent(@Nullable PsiFile psiFile) {
    myPsiFile = psiFile;
  }

  /**
   * @return The PSI file whose code style settings has changed, or null if the change is project-wide.
   */
  @Nullable
  public final PsiFile getPsiFile() {
    return myPsiFile;
  }
}
