// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.navigationToolbar.ui;

import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public enum ImageType {

  ACTIVE_FLOATING, NEXT_ACTIVE_FLOATING, INACTIVE_FLOATING,
  ACTIVE, NEXT_ACTIVE, INACTIVE,
  ACTIVE_NO_TOOLBAR, NEXT_ACTIVE_NO_TOOLBAR, INACTIVE_NO_TOOLBAR,
  ;

  public static @NotNull ImageType from(boolean floating, boolean toolbarVisible, boolean selected, boolean nextSelected) {
    if (floating) {
      return selected ? ACTIVE_FLOATING : nextSelected ? NEXT_ACTIVE_FLOATING : INACTIVE_FLOATING;
    }
    else if (toolbarVisible) {
      return selected ? ACTIVE : nextSelected ? NEXT_ACTIVE : INACTIVE;
    }
    else {
      return selected ? ACTIVE_NO_TOOLBAR : nextSelected ? NEXT_ACTIVE_NO_TOOLBAR : INACTIVE_NO_TOOLBAR;
    }
  }
}
