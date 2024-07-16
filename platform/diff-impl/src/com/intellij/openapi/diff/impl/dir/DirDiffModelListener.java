// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.dir;

import org.jetbrains.annotations.ApiStatus;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public interface DirDiffModelListener {
  void updateStarted();
  void updateFinished();
}
