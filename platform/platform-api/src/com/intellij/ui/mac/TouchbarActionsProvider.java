// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac;

import com.intellij.openapi.actionSystem.ActionGroup;
import org.jetbrains.annotations.Nullable;

public interface TouchbarActionsProvider {
  @Nullable
  ActionGroup getTouchbarActions();
}
