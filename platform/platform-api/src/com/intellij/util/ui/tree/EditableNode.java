// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui.tree;

import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Experimental
public interface EditableNode {
  /**
   * @param presentation the presentation of visible tree.StartEditingAction to configure
   */
  void updateAction(@NotNull Presentation presentation);
}
