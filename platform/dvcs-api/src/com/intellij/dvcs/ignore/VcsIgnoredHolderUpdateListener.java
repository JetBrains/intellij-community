// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ignore;

import com.intellij.openapi.vcs.FilePath;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.EventListener;

public interface VcsIgnoredHolderUpdateListener extends EventListener {

  default void updateStarted() {}

  default void updateFinished(@NotNull Collection<FilePath> ignoredPaths) {}

}
