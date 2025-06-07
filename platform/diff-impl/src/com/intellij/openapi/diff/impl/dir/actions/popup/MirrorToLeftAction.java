// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir.actions.popup;

import com.intellij.ide.diff.DirDiffElement;
import com.intellij.ide.diff.DirDiffOperation;
import com.intellij.openapi.diff.impl.dir.DirDiffElementImpl;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public class MirrorToLeftAction extends SetOperationToBase {
  @Override
  protected boolean isEnabledFor(DirDiffElement element) {
    return true;
  }

  @Override
  protected @NotNull DirDiffOperation getOperation(DirDiffElementImpl element) {
    if (element.isSource()) {
      return DirDiffOperation.DELETE;
    }
    else {
      return DirDiffOperation.COPY_FROM;
    }
  }
}
