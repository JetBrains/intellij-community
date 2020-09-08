// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.openapi.util.NlsActions;
import org.jetbrains.annotations.NotNull;

public interface NewProjectOrModuleAction {
  @NotNull @NlsActions.ActionText String getActionText(boolean isInNewSubmenu, boolean isInJavaIde);
}
