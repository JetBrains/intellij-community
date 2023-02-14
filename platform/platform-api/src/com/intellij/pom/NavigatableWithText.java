// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.pom;

import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.Nullable;


public interface NavigatableWithText extends Navigatable {
  @NlsActions.ActionText @Nullable String getNavigateActionText(boolean focusEditor);
}
