// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.reference;

import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public final class RefProjectImpl extends RefEntityImpl implements RefProject {
  RefProjectImpl(@NotNull RefManager refManager) {
    super(refManager.getProject().getName(), refManager);
  }

  @Override
  public boolean isValid() {
    return true;
  }

  @Override
  public Icon getIcon(final boolean expanded) {
    return PlatformIcons.PROJECT_ICON;
  }
}
