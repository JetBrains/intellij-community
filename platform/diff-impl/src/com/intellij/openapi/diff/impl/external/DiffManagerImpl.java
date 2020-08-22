// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.diff.impl.external;

import com.intellij.openapi.diff.DiffManager;
import com.intellij.openapi.diff.DiffTool;
import com.intellij.openapi.vcs.changes.actions.migrate.MigrateDiffTool;

@Deprecated
public class DiffManagerImpl extends DiffManager {
  @Override
  public DiffTool getIdeaDiffTool() {
    return MigrateDiffTool.INSTANCE;
  }

  @Override
  public DiffTool getDiffTool() {
    return MigrateDiffTool.INSTANCE;
  }
}
