// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.projectView.impl;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;


@ApiStatus.Internal
public final class ModuleGroupUrl extends AbstractUrl {
  private static final @NonNls String ELEMENT_TYPE = TYPE_MODULE_GROUP;

  public ModuleGroupUrl(String url) {
    super(url, null, ELEMENT_TYPE);
  }

  @Override
  protected AbstractUrl createUrl(String moduleName, String url) {
      return new ModuleGroupUrl(url);
  }
}
