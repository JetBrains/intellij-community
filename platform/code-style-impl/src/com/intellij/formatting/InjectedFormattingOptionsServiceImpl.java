// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class InjectedFormattingOptionsServiceImpl implements InjectedFormattingOptionsService {
  @Override
  public boolean shouldDelegateToTopLevel(@NotNull PsiFile file) {
    for (var provider: InjectedFormattingOptionsProvider.EP_NAME.getExtensions()) {
      var result = provider.shouldDelegateToTopLevel(file);
      if (result == null) continue;
      return result;
    }
    // We delegate formatting to a top-level file by default, that's how it worked for a long time
    return true;
  }
}
