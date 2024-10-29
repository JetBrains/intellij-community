// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.folding.impl.actions;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ExpandToLevel4Action extends BaseExpandToLevelAction {
  public ExpandToLevel4Action() {
    super(4, false);
  }
}
