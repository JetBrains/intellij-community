// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl.actions;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ExpandAllToLevel4Action extends BaseExpandToLevelAction {
  public ExpandAllToLevel4Action() {
    super(4, true);
  }
}
