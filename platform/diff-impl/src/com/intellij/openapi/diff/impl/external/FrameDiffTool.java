// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diff.DiffRequest;
import com.intellij.openapi.diff.impl.DiffPanelImpl;
import com.intellij.openapi.vcs.changes.actions.migrate.MigrateToNewDiffUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

@Deprecated
public class FrameDiffTool {
  protected DiffPanelImpl createDiffPanelImpl(@NotNull DiffRequest request, @Nullable Window window, @NotNull Disposable parentDisposable) {
    com.intellij.diff.requests.DiffRequest newRequest = MigrateToNewDiffUtil.convertRequest(request);
    return new DiffPanelImpl(request.getProject(), newRequest, parentDisposable);
  }
}
