// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.branch;

import org.jetbrains.annotations.NotNull;

/**
 * @see MultiRootBranches#isSyncOptionEnabled(DvcsSyncSettings)
 * @see RepositoryManager#isSyncEnabled()
 */
public interface DvcsSyncSettings {

  enum Value {
    SYNC,
    DONT_SYNC,
    NOT_DECIDED
  }

  @NotNull
  Value getSyncSetting();

  void setSyncSetting(@NotNull Value syncSetting);

}
