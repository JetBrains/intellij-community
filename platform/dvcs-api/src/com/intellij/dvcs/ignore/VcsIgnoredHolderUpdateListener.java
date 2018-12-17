// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore;

import com.intellij.dvcs.repo.AsyncFilesManagerListener;
import org.jetbrains.annotations.NotNull;

public interface VcsIgnoredHolderUpdateListener extends AsyncFilesManagerListener {

  default void updateStarted(@NotNull String ignorePath) {}

  default void updateFinished(@NotNull String ignorePath) {}

  @Override
  default void updateStarted() {}

  @Override
  default void updateFinished() {}
}
